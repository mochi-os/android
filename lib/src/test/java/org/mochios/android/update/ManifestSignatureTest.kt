// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

/**
 * The update manifest decides which version to fetch and which SHA-256 to
 * accept, so an unverified one lets whoever serves the android subtree freeze
 * every client on an old release. These drive the pure half of the check with
 * a generated key pair - the release key is not in the tree.
 */
class ManifestSignatureTest {

    private val manifest = """{"tracks":{"production":"0.19"},"releases":{}}"""

    private fun pair(): KeyPair =
        KeyPairGenerator.getInstance("Ed25519").generateKeyPair()

    /** The raw 32 key bytes out of the X.509 encoding, base64, as published. */
    private fun published(pair: KeyPair): String =
        Base64.getEncoder().encodeToString(pair.public.encoded.takeLast(32).toByteArray())

    private fun sign(pair: KeyPair, body: String): String {
        val signer = Signature.getInstance("Ed25519")
        signer.initSign(pair.private)
        signer.update(body.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(signer.sign())
    }

    @Test
    fun `a manifest signed by the release key verifies`() {
        val key = pair()
        assertTrue(
            UpdateChecker.signature_verified(manifest, sign(key, manifest), published(key))
        )
    }

    @Test
    fun `a tampered manifest does not verify`() {
        val key = pair()
        val signature = sign(key, manifest)
        val tampered = manifest.replace("0.19", "0.01")
        assertFalse(UpdateChecker.signature_verified(tampered, signature, published(key)))
    }

    @Test
    fun `a signature from another key does not verify`() {
        val key = pair()
        val attacker = pair()
        assertFalse(
            UpdateChecker.signature_verified(manifest, sign(attacker, manifest), published(key))
        )
    }

    @Test
    fun `a malformed signature or key does not verify`() {
        val key = pair()
        assertFalse(UpdateChecker.signature_verified(manifest, "", published(key)))
        assertFalse(UpdateChecker.signature_verified(manifest, "not-base64!!", published(key)))
        assertFalse(UpdateChecker.signature_verified(manifest, sign(key, manifest), "short"))
    }
}
