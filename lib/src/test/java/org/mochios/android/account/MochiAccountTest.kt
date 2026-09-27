// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.account

import org.junit.Assert.assertEquals
import org.junit.Test

class MochiAccountTest {
    private val identity = "1AbjvmLhRMEbEXH7g42WNCVz53rWKAQSnybWUymwfApFaZc41r"

    @Test
    fun namesTheAccountAfterItsEmailAddress() {
        assertEquals(
            "alistair@example.org",
            MochiAccount.label(" alistair@example.org ", "Alistair", "https://mochi-os.org", identity),
        )
    }

    @Test
    fun fallsBackToTheNameAndTheHostItSignsInTo() {
        assertEquals(
            "Alistair (mochi-os.org)",
            MochiAccount.label("", "Alistair", "https://mochi-os.org/", identity),
        )
        assertEquals("mochi-os.org", MochiAccount.label("", " ", "https://mochi-os.org", identity))
    }

    @Test
    fun usesTheIdentityOnlyWhenNothingElseIsKnown() {
        assertEquals(identity, MochiAccount.label("", "", "", identity))
    }

    @Test
    fun readsTheHostFromAnyServerAddress() {
        assertEquals("mochi-os.org", MochiAccount.host("https://mochi-os.org"))
        assertEquals("192.168.1.10", MochiAccount.host("http://192.168.1.10:8081/"))
        assertEquals("mochi-os.org", MochiAccount.host("mochi-os.org"))
    }

    @Test
    fun keepsTwoAccountsWithOneEmailAddressApart() {
        val taken = setOf("alistair@example.org")
        assertEquals("alistair@example.org", MochiAccount.unique("alistair@example.org", "yuzu.example", emptySet()))
        assertEquals(
            "alistair@example.org (yuzu.example)",
            MochiAccount.unique("alistair@example.org", "yuzu.example", taken),
        )
        assertEquals(
            "alistair@example.org (yuzu.example) 2",
            MochiAccount.unique("alistair@example.org", "yuzu.example", taken + "alistair@example.org (yuzu.example)"),
        )
    }

    @Test
    fun numbersANameThatAlreadyCarriesItsHost() {
        val wanted = "Alistair (mochi-os.org)"
        assertEquals("$wanted 2", MochiAccount.unique(wanted, "mochi-os.org", setOf(wanted)))
    }
}
