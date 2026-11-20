package com.plcoding.cardigan.user.domain.model

/** The rate-limited auth endpoints. Each one counts on its own. */
enum class AuthAction {
    LOGIN,
    REGISTER,
}
