package com.anaglych.squintboyadvance.data.cloud

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await

sealed class DriveAuthResult {
    data class Token(val accessToken: String) : DriveAuthResult()

    /** User consent needed: launch this and then call [DriveAuthManager.authorize] again. */
    data class NeedsConsent(val pendingIntent: PendingIntent) : DriveAuthResult()

    data class Failed(val message: String) : DriveAuthResult()
}

/**
 * Drive OAuth via Play Services AuthorizationClient with the non-restricted
 * `drive.file` scope (the app only ever sees files it created). The same
 * silent [authorize] call is the token-refresh path for Workers — it returns
 * a fresh token without UI once consent has been granted, and reports
 * NeedsConsent (never launches UI) when consent was revoked.
 */
class DriveAuthManager(private val context: Context) {

    companion object {
        private const val TAG = "DriveAuthManager"
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
    }

    suspend fun authorize(): DriveAuthResult {
        return try {
            val request = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
                .build()
            val result = Identity.getAuthorizationClient(context).authorize(request).await()
            when {
                result.hasResolution() -> {
                    val intent = result.pendingIntent
                    if (intent != null) DriveAuthResult.NeedsConsent(intent)
                    else DriveAuthResult.Failed("Consent required but no resolution intent")
                }
                result.accessToken != null -> DriveAuthResult.Token(result.accessToken!!)
                else -> DriveAuthResult.Failed("No token in authorization result")
            }
        } catch (e: Exception) {
            Log.w(TAG, "authorize failed: ${e.message}")
            DriveAuthResult.Failed(e.message ?: "Authorization failed")
        }
    }

    /** Completes the consent flow from the resolution activity result. */
    fun tokenFromConsentResult(data: Intent?): String? {
        return try {
            Identity.getAuthorizationClient(context)
                .getAuthorizationResultFromIntent(data)
                .accessToken
        } catch (e: Exception) {
            Log.w(TAG, "Consent result parse failed: ${e.message}")
            null
        }
    }

    /** Worker-safe token fetch: token or null, never UI. */
    suspend fun getAccessTokenSilent(): String? =
        (authorize() as? DriveAuthResult.Token)?.accessToken
}
