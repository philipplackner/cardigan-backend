package com.plcoding.cardigan.user.domain.model

import java.security.MessageDigest
import java.time.Instant
import java.util.HexFormat
import java.util.UUID

/**
 * A refresh token of a session, known only by the hash of its value. A rotated token was already
 * exchanged for a newer one and stays on record, so using it again is detected.
 */
data class RefreshToken(
    val id: UUID,
    val sessionId: UUID,
    val tokenHash: String,
    val createdAt: Instant,
    val expiresAt: Instant,
    val rotatedAt: Instant? = null,
) {

    val isRotated: Boolean get() = rotatedAt != null

    /** A token is expired from the instant its expiry is reached. */
    fun isExpiredAt(now: Instant): Boolean = !expiresAt.isAfter(now)

    override fun toString(): String =
        "RefreshToken(id=$id, sessionId=$sessionId, createdAt=$createdAt, expiresAt=$expiresAt, rotatedAt=$rotatedAt)"
}

/** The stored form of a refresh token: the lowercase hex SHA-256 of its UTF-8 bytes. */
fun hashRefreshToken(token: String): String =
    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8)))
