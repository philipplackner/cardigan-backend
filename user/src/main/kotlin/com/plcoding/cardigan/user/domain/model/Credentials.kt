package com.plcoding.cardigan.user.domain.model

/**
 * What register and login work with: the email from [normalizeEmail], the NFC-normalized password
 * and the transport of the session to start.
 */
data class Credentials(
    val email: String,
    val password: String,
    val transport: RefreshTokenTransport,
) {

    override fun toString(): String = "Credentials(email=$email, transport=$transport)"
}
