// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import android.content.Context
import android.util.Log
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.mochios.android.BuildConfig
import org.mochios.android.auth.AuthRepository
import org.mochios.android.util.isServerOrigin
import org.unifiedpush.android.connector.MessagingReceiver
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage

/**
 * Path only when [endpointUrl] is on [server]'s own origin, so the server takes
 * its local-delivery fast-path; any other endpoint is returned unchanged for
 * RFC 8030. Compare the whole origin, not the host: a wrong verdict either way
 * loses the push.
 */
internal fun collapseLocalEndpoint(endpointUrl: String, server: String): String {
    val ep = endpointUrl.toHttpUrlOrNull() ?: return endpointUrl
    // Our distributor never issues an endpoint carrying credentials or a
    // fragment, so refuse to treat one as local rather than reason about it.
    if (ep.encodedUsername.isNotEmpty() || ep.encodedPassword.isNotEmpty() || ep.fragment != null) {
        return endpointUrl
    }
    return if (isServerOrigin(ep, server)) ep.encodedPath else endpointUrl
}

/**
 * The UnifiedPush connector's callbacks for this app: the endpoint the
 * distributor hands out is registered with the server, and a message is
 * delivered through [deliverPush]. The instance is the identity the
 * registration was made for ([MochiPushClient.register]).
 *
 * Only a distributor's broadcasts arrive here. This app's own distributor,
 * [PushService], delivers in process instead, so nothing on this exported
 * receiver can raise a notification without the connector's decrypt.
 */
class MochiPushReceiver : MessagingReceiver() {

    private fun deps(context: Context): PushEntryPoint =
        EntryPointAccessors.fromApplication(context.applicationContext, PushEntryPoint::class.java)

    override fun onNewEndpoint(context: Context, endpoint: PushEndpoint, instance: String) {
        // The endpoint URL ends in the subscription id, which is the unguessable
        // capability anyone can post a push to, so log only its origin; the
        // instance is the identity, and stays out of a release log.
        Log.i(TAG, "onNewEndpoint")
        if (BuildConfig.DEBUG) Log.d(TAG, "onNewEndpoint instance=$instance host=${endpointHost(endpoint.url)}")
        val keys = endpoint.pubKeySet
        if (keys == null) {
            Log.w(TAG, "Endpoint has no Web Push keys — server can't encrypt; aborting")
            return
        }
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val deps = deps(context)
                val server = deps.sessionManager().getServerUrlBlocking()
                // When the distributor's endpoint is on our own Mochi server,
                // send the path-only form so the server can take its in-process
                // WebSocket fast-path instead of POSTing RFC 8030 back to itself
                // (which would hit the still-stubbed inbound endpoint and 501).
                val endpointToSend = collapseLocalEndpoint(endpoint.url, server)
                // Which server the user is on stays out of a release log.
                Log.i(TAG, "register: local=${endpointToSend != endpoint.url}")
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "register: endpoint host=${endpointHost(endpoint.url)} server=$server")
                }
                // The distributor re-announces the endpoint on every register
                // call, and PushTransport registers on every resume; post it only
                // when the server has not already accepted or refused it.
                val credential = RegistrationMemo.fingerprint(endpointToSend, keys.auth, keys.pubKey)
                val store = RegistrationStore(context)
                val now = System.currentTimeMillis()
                val verdict = RegistrationMemo.judge(
                    now, server, PushTransport.TRANSPORT_UNIFIEDPUSH, credential, store.last(), store.refusal()
                )
                if (verdict != RegistrationMemo.Verdict.REGISTER) {
                    Log.i(TAG, "register: endpoint already ${verdict.name.lowercase()}; not re-posting")
                    return@launch
                }
                val answer = postPushRegister(
                    deps.okHttpClient(),
                    deps.authRepository(),
                    server = server,
                    label = DeviceName.resolve(context),
                    auth = keys.auth,
                    p256dh = keys.pubKey,
                    endpoint = endpointToSend,
                    device = deps.deviceStore().id(),
                )
                val registration = Registration(server, PushTransport.TRANSPORT_UNIFIEDPUSH, credential, now)
                when {
                    answer == null -> {}
                    answer.accepted -> {
                        store.success(registration)
                        if (answer.account != null) deps.pushAccountStore().store(instance, answer.account)
                    }
                    answer.refused -> {
                        store.refuse(registration)
                        Log.w(TAG, "/notifications/-/push/register refused ${answer.code}; not retrying for a day")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to register endpoint with Mochi server: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    /** Host of [endpointUrl], for logging that must not carry the subscription id. */
    private fun endpointHost(endpointUrl: String): String =
        runCatching { android.net.Uri.parse(endpointUrl).host }.getOrNull() ?: "unparseable"

    override fun onUnregistered(context: Context, instance: String) {
        Log.i(TAG, "onUnregistered")
        val store = deps(context).pushAccountStore()
        val accountId = store.read(instance) ?: return
        store.clear(instance)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val deps = deps(context)
                val server = deps.sessionManager().getServerUrlBlocking()
                postPushAccountsRemove(
                    deps.okHttpClient(),
                    deps.authRepository(),
                    server,
                    accountId
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister endpoint from Mochi server: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onRegistrationFailed(
        context: Context,
        reason: org.unifiedpush.android.connector.FailedReason,
        instance: String,
    ) {
        Log.w(TAG, "onRegistrationFailed reason=$reason")
    }

    override fun onMessage(context: Context, message: PushMessage, instance: String) {
        // The only authenticity gate on push content from a third-party
        // distributor: the connector calls this with decrypted = false after a
        // failed decrypt, so without the check anyone holding the endpoint URL
        // can post an arbitrary notification.
        if (!message.decrypted) {
            Log.w(TAG, "Push payload was not decrypted; ignoring")
            return
        }
        deliverPush(context, message.content, instance)
    }

    private suspend fun postPushRegister(
        client: OkHttpClient,
        authRepository: AuthRepository,
        server: String,
        label: String,
        auth: String,
        p256dh: String,
        endpoint: String,
        device: String,
    ): Answer? {
        val token =
            authRepository.fetchToken("notifications").getOrNull() ?: return null
        val url = server.trimEnd('/') + "/notifications/-/push/register"
        val form = FormBody.Builder()
            .add("label", label)
            .add("auth", auth)
            .add("p256dh", p256dh)
            .add("endpoint", endpoint)
            .build()
        // The Device header binds the push account to this device, so a later
        // registration from the same phone replaces it rather than adding to it.
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Device", device)
            .post(form)
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                Log.w(TAG, "/notifications/-/push/register returned ${resp.code}")
                return Answer(resp.code, null)
            }
            val body = resp.body?.string().orEmpty()
            val account = try {
                JSONObject(body).optJSONObject("data")?.optString("id")?.takeIf { it.isNotBlank() }
            } catch (_: Exception) {
                Log.w(TAG, "Could not parse /notifications/-/push/register response")
                null
            }
            return Answer(resp.code, account)
        }
    }

    private suspend fun postPushAccountsRemove(
        client: OkHttpClient,
        authRepository: AuthRepository,
        server: String,
        accountId: String,
    ) {
        val token =
            authRepository.fetchToken("notifications").getOrNull() ?: return
        val url = server.trimEnd('/') + "/notifications/-/push/accounts/remove"
        val form = FormBody.Builder().add("id", accountId).build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .post(form)
            .build()
        client.newCall(request).execute().close()
    }

    private companion object {
        const val TAG = "MochiPush"
    }
}
