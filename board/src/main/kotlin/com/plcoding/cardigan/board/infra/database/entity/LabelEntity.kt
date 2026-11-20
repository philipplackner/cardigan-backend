package com.plcoding.cardigan.board.infra.database.entity

import com.plcoding.cardigan.board.domain.model.LabelColor
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "labels",
    schema = "board_service",
    uniqueConstraints = [
        UniqueConstraint(name = LabelEntity.OWNER_NAME_KEY_UNIQUE, columnNames = ["owner_id", "name_key"]),
    ],
)
class LabelEntity(
    @Id
    var id: UUID,

    @Column(name = "owner_id", nullable = false)
    var ownerId: UUID,

    @Column(nullable = false, length = 30)
    var name: String,

    // Case folding can turn one code point into up to three.
    @Column(name = "name_key", nullable = false, length = 90)
    var nameKey: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 7)
    var color: LabelColor,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
) {

    companion object {
        const val OWNER_NAME_KEY_UNIQUE = "labels_owner_name_key_unique"
    }
}
