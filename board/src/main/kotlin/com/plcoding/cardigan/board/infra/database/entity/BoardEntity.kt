package com.plcoding.cardigan.board.infra.database.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "boards",
    schema = "board_service",
    uniqueConstraints = [
        // Starts with owner_id, so its index also serves the board list query.
        UniqueConstraint(name = BoardEntity.OWNER_KEY_UNIQUE, columnNames = ["owner_id", "key"]),
    ],
)
class BoardEntity(
    @Id
    var id: UUID,

    @Column(name = "owner_id", nullable = false, updatable = false)
    var ownerId: UUID,

    @Column(nullable = false, length = 100)
    var name: String,

    @Column(name = "key", nullable = false, updatable = false, length = 5)
    var key: String,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
) {

    companion object {
        const val OWNER_KEY_UNIQUE = "boards_owner_key_unique"
    }
}
