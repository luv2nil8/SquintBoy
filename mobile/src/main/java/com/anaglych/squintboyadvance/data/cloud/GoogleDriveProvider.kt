package com.anaglych.squintboyadvance.data.cloud

import android.content.Context
import android.util.Log
import java.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * Drive REST v3 over OkHttp — exactly the four endpoints this feature needs
 * (folder create, multipart upload, list query, delete) plus `about` for the
 * account email. The google-api-client stack is deliberately avoided: it is a
 * heavyweight transitive tree that fights R8 and predates AuthorizationClient.
 *
 * Layout mirrors the local archive: "SquintBoy Saves"/{gameDirName}/{fileName},
 * with sha256+romId stored in appProperties.
 */
/** Minimal persistent string cache so the provider stays JVM-testable. */
interface StringCache {
    fun get(key: String): String?
    fun put(key: String, value: String)
}

private class PrefsStringCache(context: Context, name: String) : StringCache {
    private val prefs = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
    override fun get(key: String): String? = prefs.getString(key, null)
    override fun put(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
}

class GoogleDriveProvider(
    private val tokenProvider: suspend () -> String?,
    private val idCache: StringCache,
    private val client: OkHttpClient = OkHttpClient(),
    private val apiBase: String = "https://www.googleapis.com/drive/v3",
    private val uploadBase: String = "https://www.googleapis.com/upload/drive/v3",
) : CloudProvider {

    constructor(context: Context, tokenProvider: suspend () -> String?) : this(
        tokenProvider = tokenProvider,
        idCache = PrefsStringCache(context, PREFS_NAME),
    )

    companion object {
        private const val TAG = "GoogleDriveProvider"
        private const val ROOT_FOLDER_NAME = "SquintBoy Saves"
        private const val FOLDER_MIME = "application/vnd.google-apps.folder"
        private const val PREFS_NAME = "drive_ids"
        private const val KEY_ROOT_ID = "root_folder_id"
    }

    override val id: String = "gdrive"

    private val json = Json { ignoreUnknownKeys = true }

    // ── CloudProvider ──────────────────────────────────────────────────

    override suspend fun ensureGameDir(gameDirName: String): CloudResult<String> {
        val rootId = when (val root = ensureRootFolder()) {
            is CloudResult.Ok -> root.value
            else -> return root
        }
        val cached = idCache.get("dir_$gameDirName")
        if (cached != null) return CloudResult.Ok(cached)

        return ensureFolder(gameDirName, rootId).also { result ->
            if (result is CloudResult.Ok) idCache.put("dir_$gameDirName", result.value)
        }
    }

    override suspend fun upload(
        remoteDirId: String,
        fileName: String,
        bytes: ByteArray,
        sha256: String,
        romId: String,
    ): CloudResult<String> {
        // Adopt an orphan from a crashed prior run before creating a duplicate.
        when (val existing = findByName(fileName, remoteDirId)) {
            is CloudResult.Ok -> existing.value?.let { return CloudResult.Ok(it) }
            is CloudResult.Retryable -> return existing
            is CloudResult.Permanent -> return existing
            is CloudResult.NotConnected -> return existing
            is CloudResult.QuotaExceeded -> return existing
        }

        val metadata = buildJsonObject {
            put("name", fileName)
            putJsonArray("parents") { add(remoteDirId) }
            putJsonObject("appProperties") {
                put("sha256", sha256)
                put("romId", romId)
            }
        }
        val body = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(metadata.toString().toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .addPart(bytes.toRequestBody("application/octet-stream".toMediaType()))
            .build()

        return execute(
            Request.Builder()
                .url("$uploadBase/files?uploadType=multipart&fields=id")
                .post(body)
        ) { response ->
            val fileId = json.parseToJsonElement(response.body!!.string())
                .jsonObject["id"]?.jsonPrimitive?.content
                ?: return@execute CloudResult.Permanent("Upload response missing id")
            CloudResult.Ok(fileId)
        }
    }

    override suspend fun delete(remoteFileId: String): CloudResult<Unit> {
        return execute(
            Request.Builder().url("$apiBase/files/$remoteFileId").delete(),
            notFoundIsOk = true,
        ) { CloudResult.Ok(Unit) }
    }

    /** Account email for the settings UI. */
    suspend fun accountEmail(): CloudResult<String> {
        return execute(
            Request.Builder().url("$apiBase/about?fields=user(emailAddress)").get()
        ) { response ->
            val email = json.parseToJsonElement(response.body!!.string())
                .jsonObject["user"]?.jsonObject?.get("emailAddress")?.jsonPrimitive?.content
                ?: return@execute CloudResult.Permanent("No email in about response")
            CloudResult.Ok(email)
        }
    }

    // ── Folders ────────────────────────────────────────────────────────

    private suspend fun ensureRootFolder(): CloudResult<String> {
        val cached = idCache.get(KEY_ROOT_ID)
        if (cached != null) return CloudResult.Ok(cached)
        return ensureFolder(ROOT_FOLDER_NAME, "root").also { result ->
            if (result is CloudResult.Ok) idCache.put(KEY_ROOT_ID, result.value)
        }
    }

    private suspend fun ensureFolder(name: String, parentId: String): CloudResult<String> {
        when (val found = findByName(name, parentId, mimeType = FOLDER_MIME)) {
            is CloudResult.Ok -> found.value?.let { return CloudResult.Ok(it) }
            is CloudResult.Retryable -> return found
            is CloudResult.Permanent -> return found
            is CloudResult.NotConnected -> return found
            is CloudResult.QuotaExceeded -> return found
        }
        val metadata = buildJsonObject {
            put("name", name)
            put("mimeType", FOLDER_MIME)
            putJsonArray("parents") { add(parentId) }
        }
        return execute(
            Request.Builder()
                .url("$apiBase/files?fields=id")
                .post(metadata.toString().toRequestBody("application/json; charset=UTF-8".toMediaType()))
        ) { response ->
            val folderId = json.parseToJsonElement(response.body!!.string())
                .jsonObject["id"]?.jsonPrimitive?.content
                ?: return@execute CloudResult.Permanent("Create-folder response missing id")
            CloudResult.Ok(folderId)
        }
    }

    /** files.list by exact name under a parent. Ok(null) = not found. */
    private suspend fun findByName(
        name: String,
        parentId: String,
        mimeType: String? = null,
    ): CloudResult<String?> {
        val escaped = name.replace("'", "\\'")
        var q = "name='$escaped' and '$parentId' in parents and trashed=false"
        if (mimeType != null) q += " and mimeType='$mimeType'"
        val url = "$apiBase/files?q=${java.net.URLEncoder.encode(q, "UTF-8")}&fields=files(id)&pageSize=1"
        return execute(Request.Builder().url(url).get()) { response ->
            val files = json.parseToJsonElement(response.body!!.string())
                .jsonObject["files"]?.jsonArray
            CloudResult.Ok(
                files?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.content
            )
        }
    }

    // ── Transport + error mapping ──────────────────────────────────────

    private suspend fun <T> execute(
        requestBuilder: Request.Builder,
        notFoundIsOk: Boolean = false,
        retriedAuth: Boolean = false,
        onSuccess: (Response) -> CloudResult<T>,
    ): CloudResult<T> {
        val token = tokenProvider() ?: return CloudResult.NotConnected
        val request = requestBuilder
            .header("Authorization", "Bearer $token")
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> onSuccess(response)
                    response.code == 404 && notFoundIsOk ->
                        @Suppress("UNCHECKED_CAST") (CloudResult.Ok(Unit) as CloudResult<T>)
                    response.code == 401 && !retriedAuth ->
                        // One silent re-auth: AuthorizationClient refreshes the token.
                        execute(requestBuilder, notFoundIsOk, retriedAuth = true, onSuccess)
                    response.code == 401 -> CloudResult.Permanent("AUTH")
                    response.code == 403 && isQuotaExceeded(response) -> CloudResult.QuotaExceeded
                    response.code == 403 -> CloudResult.Permanent("Forbidden: ${response.message}")
                    response.code == 429 || response.code >= 500 ->
                        CloudResult.Retryable("HTTP ${response.code}")
                    else -> CloudResult.Permanent("HTTP ${response.code}")
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Drive I/O error: ${e.message}")
            CloudResult.Retryable(e.message ?: "I/O error")
        } catch (e: Exception) {
            Log.e(TAG, "Drive call failed", e)
            CloudResult.Permanent(e.message ?: "Unexpected error")
        }
    }

    private fun isQuotaExceeded(response: Response): Boolean {
        return try {
            val body = response.peekBody(8192).string()
            body.contains("storageQuotaExceeded")
        } catch (_: Exception) {
            false
        }
    }
}
