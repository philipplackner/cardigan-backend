package com.plcoding.cardigan.user.domain.repository

/**
 * Hashes and verifies passwords. Callers pass the NFC-normalized password. Both functions throw
 * `PasswordHashingBusyException` when the hasher is at capacity for too long.
 */
interface PasswordHasher {

    fun hash(password: String): String

    /**
     * True when [password] belongs to [hash]. With a `null` hash it does the same work against a
     * dummy hash and returns false, so an unknown user costs as much as a known one.
     */
    fun matches(password: String, hash: String?): Boolean
}
