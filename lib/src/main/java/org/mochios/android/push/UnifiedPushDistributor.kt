package org.mochios.android.push

/**
 * Mochi's UnifiedPush *distributor* implementation — the on-device
 * service that holds the long-lived connection to the user's Mochi
 * server(s) and routes incoming pushes to per-app apps via the UP
 * MESSAGE intent.
 *
 * **NOT YET IMPLEMENTED.** Skeleton placeholder so the rest of the
 * push module can compile against the expected file layout. The
 * actual distributor is a substantial engineering project on its own:
 *
 *   - Foreground Service with `specialUse` type, declared in a
 *     manifest fragment that's pulled in only by the shell app
 *     (org.mochios.mochi) so the system distributor picker doesn't
 *     show one entry per Mochi app.
 *   - Multiplex one WebSocket per logged-in [org.mochios.android.account.MochiAccount.Snapshot]
 *     to the corresponding Mochi server.
 *   - On REGISTER intent from a UP Application: allocate a per-Application
 *     subscription with the user's Mochi server (via
 *     `/notifications/-/push/register`), generate a P-256 keypair, and return
 *     the endpoint URL via the NEW_ENDPOINT broadcast.
 *   - On WebSocket message from server: decode envelope, send MESSAGE
 *     intent to the registered Application package.
 *   - Reconnect with jittered exponential backoff; refresh expired
 *     session cookies via [org.mochios.android.account.MochiAccount].
 *
 * Until this lands, per-app Mochi apps run UnifiedPush against a
 * third-party distributor (ntfy, NextPush) the user installs
 * separately. [MochiPushClient] + [MochiPushReceiver] handle that
 * path today.
 *
 * See `claude/plans/android-notifications.md` for the full design.
 */
internal object UnifiedPushDistributor {
    // Intentionally empty. See class doc for status.
}
