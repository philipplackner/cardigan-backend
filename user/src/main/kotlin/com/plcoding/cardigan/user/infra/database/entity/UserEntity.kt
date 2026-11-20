package com.plcoding.cardigan.user.infra.database.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.Check
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "users",
    schema = "user_service",
    uniqueConstraints = [UniqueConstraint(name = UserEntity.EMAIL_UNIQUE_CONSTRAINT, columnNames = ["email"])],
)
@Check(name = "users_email_lowercase", constraints = "email = lower(email)")
class UserEntity(
    @Id
    var id: UUID,

    @Column(nullable = false, length = 254)
    var email: String,

    @Column(name = "password_hash", nullable = false)
    var passwordHash: String,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
) {

    companion object {
        const val EMAIL_UNIQUE_CONSTRAINT = "users_email_unique"
    }
}
