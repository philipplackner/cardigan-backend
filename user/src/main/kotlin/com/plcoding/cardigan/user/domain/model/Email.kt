package com.plcoding.cardigan.user.domain.model

import com.plcoding.cardigan.common.domain.text.normalizeText
import java.util.Locale

/** The longest email a user can have, in code points after normalization. */
const val EMAIL_MAX_LENGTH = 254

/**
 * The form an email is stored and compared in: `text-length` normalization, then lowercase. Register,
 * login and the rate limit key all use it, so one address is one user everywhere.
 */
fun normalizeEmail(email: String): String = normalizeText(email).lowercase(Locale.ROOT)
