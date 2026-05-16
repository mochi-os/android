package org.mochios.android.notifications

import org.mochios.android.api.toMochiError
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationsRepository @Inject constructor(
    private val api: NotificationsApi,
) {

    suspend fun list(): NotificationsListResponse {
        try {
            val resp = api.list()
            if (resp.isSuccessful) return resp.body() ?: NotificationsListResponse()
            throw RuntimeException("HTTP ${resp.code()}")
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun count(): NotificationsCount {
        try {
            val resp = api.count()
            if (resp.isSuccessful) return resp.body()?.data ?: NotificationsCount()
            throw RuntimeException("HTTP ${resp.code()}")
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun markRead(id: String) {
        try {
            val resp = api.read(id)
            if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code()}")
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun markAllRead() {
        try {
            val resp = api.readAll()
            if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code()}")
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun clearAll() {
        try {
            val resp = api.clearAll()
            if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code()}")
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }
}
