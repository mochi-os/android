// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.update

import android.content.Context
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.Executors

/**
 * The schedule runs at every start of the process - a push, a sync, boot -
 * so scheduling again must keep the check already scheduled, not replace it
 * with one that runs at once and restarts the day.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UpdateScheduleTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `scheduling again keeps the scheduled check`() {
        // A worker that does nothing, so no check reaches the network.
        val factory = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                object : Worker(appContext, workerParameters) {
                    override fun doWork(): Result = Result.success()
                }
        }
        WorkManager.initialize(
            context,
            Configuration.Builder()
                .setExecutor(Executors.newSingleThreadExecutor())
                .setWorkerFactory(factory)
                .build(),
        )
        val work = WorkManager.getInstance(context)
        UpdateChecker.schedule(context)
        val first = work.getWorkInfosForUniqueWork("mochi_update_check").get().single().id
        UpdateChecker.schedule(context)
        val after = work.getWorkInfosForUniqueWork("mochi_update_check").get()
        assertEquals(listOf(first), after.map { info -> info.id })
    }
}
