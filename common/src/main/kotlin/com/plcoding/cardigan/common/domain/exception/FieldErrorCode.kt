package com.plcoding.cardigan.common.domain.exception

/**
 * Field error codes from the shared `api-errors` spec. The declaration order is the precedence
 * order: when one field breaks several rules, only the first one is reported.
 */
enum class FieldErrorCode {
    REQUIRED,
    INVALID_CHARACTER,
    TOO_SHORT,
    TOO_LONG,
    INVALID_FORMAT,
    PASSWORD_TOO_WEAK,
    INVALID_VALUE,
    OUT_OF_RANGE,
    ALREADY_USED,
}
