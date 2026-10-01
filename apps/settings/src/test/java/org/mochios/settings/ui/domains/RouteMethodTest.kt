// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.domains

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mochios.settings.R

class RouteMethodTest {

    @Test
    fun `each route method is shown by its label`() {
        assertEquals(R.string.route_method_app, routeMethodLabel("app"))
        assertEquals(R.string.route_method_entity, routeMethodLabel("entity"))
        assertEquals(R.string.route_method_redirect, routeMethodLabel("redirect"))
    }
}
