package com.plcoding.cardigan.user.infra.database.entity

import com.plcoding.cardigan.user.domain.model.RefreshTokenTransport
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.Check
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "sessions",
    schema = "user_service",
    indexes = [Index(name = "sessions_user_id_idx", columnList = "user_id")],
)
@Check(name = "sessions_transport_valid", constraints = "transport in ('COOKIE','BODY')")
class SessionEntity(
    @Id
    var id: UUID,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    var user: UserEntity,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var transport: RefreshTokenTransport,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant,

    @Column(name = "revoked_at")
    var revokedAt: Instant?,
)
