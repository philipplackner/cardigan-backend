package com.plcoding.cardigan.common.infra.security

import com.plcoding.cardigan.common.domain.text.parseCanonicalUuid
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2ErrorCodes
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Clock

private val invalidToken = OAuth2TokenValidatorResult.failure(OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN))

/** Requires `exp` and rejects the token from `exp` on, with no clock skew. */
class ExpiryValidator(
    private val clock: Clock,
) : OAuth2TokenValidator<Jwt> {
    override fun validate(token: Jwt): OAuth2TokenValidatorResult {
        val expiresAt = token.expiresAt ?: return invalidToken
        return if (clock.instant().isBefore(expiresAt)) OAuth2TokenValidatorResult.success() else invalidToken
    }
}

/** Requires `type` to be `access`, so other token kinds can't be used as access tokens. */
class TokenTypeValidator : OAuth2TokenValidator<Jwt> {
    override fun validate(token: Jwt): OAuth2TokenValidatorResult =
        if (token.getClaimAsString(JwtClaims.TYPE) == JwtClaims.TYPE_ACCESS) {
            OAuth2TokenValidatorResult.success()
        } else {
            invalidToken
        }
}

/** Requires `sub` to be a UUID. */
class SubjectValidator : OAuth2TokenValidator<Jwt> {
    override fun validate(token: Jwt): OAuth2TokenValidatorResult =
        if (token.subject?.let(::parseCanonicalUuid) != null) OAuth2TokenValidatorResult.success() else invalidToken
}
