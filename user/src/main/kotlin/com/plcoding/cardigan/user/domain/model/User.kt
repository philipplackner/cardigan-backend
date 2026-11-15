package com.plcoding.cardigan.user.domain.model

import java.time.Instant
import java.util.Locale
import java.util.UUID

/**
 * A Cardigan user. The email is stored lowercased; emails are ASCII, so lowercasing equals case
 * folding and two emails that differ only in case are the same email.
 */
data class User(
    val id: UUID,
    val email: String,
    val passwordHash: String,
    val createdAt: Instant,
    val updatedAt: Instant,
) {

    init {
        require(email == email.lowercase(Locale.ROOT)) { "Email must be lowercase" }
    }

    override fun toString(): String = "User(id=$id, email=$email, createdAt=$createdAt, updatedAt=$updatedAt)"
}
