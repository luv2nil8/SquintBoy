package com.anaglych.squintboyadvance.data.cloud

sealed class CloudResult<out T> {
    data class Ok<T>(val value: T) : CloudResult<T>()

    /** Transient (network, 5xx, rate limit): retry with backoff. */
    data class Retryable(val message: String) : CloudResult<Nothing>()

    /** Definitive failure for this item (bad request, revoked consent). */
    data class Permanent(val message: String) : CloudResult<Nothing>()

    data object NotConnected : CloudResult<Nothing>()
    data object QuotaExceeded : CloudResult<Nothing>()
}

/**
 * A cloud archive target. Google Drive is the only v1 implementation; the
 * interface is the seam for future providers (Dropbox, WebDAV, …).
 */
interface CloudProvider {
    val id: String

    /** Remote directory id for a game, creating the hierarchy as needed. */
    suspend fun ensureGameDir(gameDirName: String): CloudResult<String>

    /** Uploads a save blob; returns the remote file id. */
    suspend fun upload(
        remoteDirId: String,
        fileName: String,
        bytes: ByteArray,
        sha256: String,
        romId: String,
    ): CloudResult<String>

    /** Deletes a remote file. Already-gone counts as success. */
    suspend fun delete(remoteFileId: String): CloudResult<Unit>
}
