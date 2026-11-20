package com.plcoding.cardigan.user.infra.database.mapper

import com.plcoding.cardigan.user.domain.model.RefreshToken
import com.plcoding.cardigan.user.domain.model.Session
import com.plcoding.cardigan.user.infra.database.entity.RefreshTokenEntity
import com.plcoding.cardigan.user.infra.database.entity.SessionEntity
import com.plcoding.cardigan.user.infra.database.entity.UserEntity

fun SessionEntity.toSession(): Session = Session(
    id = id,
    userId = user.id,
    transport = transport,
    createdAt = createdAt,
    revokedAt = revokedAt,
)

fun Session.toSessionEntity(user: UserEntity): SessionEntity = SessionEntity(
    id = id,
    user = user,
    transport = transport,
    createdAt = createdAt,
    revokedAt = revokedAt,
)

fun RefreshTokenEntity.toRefreshToken(): RefreshToken = RefreshToken(
    id = id,
    sessionId = session.id,
    tokenHash = tokenHash,
    createdAt = createdAt,
    expiresAt = expiresAt,
    rotatedAt = rotatedAt,
)

fun RefreshToken.toRefreshTokenEntity(session: SessionEntity): RefreshTokenEntity = RefreshTokenEntity(
    id = id,
    session = session,
    tokenHash = tokenHash,
    createdAt = createdAt,
    expiresAt = expiresAt,
    rotatedAt = rotatedAt,
)
