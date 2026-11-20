package com.plcoding.cardigan.user.infra.database.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "refresh_tokens",
    schema = "user_service",
    uniqueConstraints = [UniqueConstraint(name = "refresh_tokens_token_hash_unique", columnNames = ["token_hash"])],
    indexes = [Index(name = "refresh_tokens_session_id_idx", columnList = "session_id")],
)
class RefreshTokenEntity(
    @Id
    var id: UUID,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    var session: SessionEntity,

    @Column(name = "token_hash", nullable = false, length = 64)
    var tokenHash: String,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant,

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,

    @Column(name = "rotated_at")
    var rotatedAt: Instant?,
)
