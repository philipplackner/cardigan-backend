package com.plcoding.cardigan.board.infra.database.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.ForeignKey
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
    name = "columns",
    schema = "board_service",
    indexes = [Index(name = "columns_board_position_idx", columnList = "board_id, position")],
)
@Check(name = "columns_position_not_negative", constraints = "position >= 0")
class ColumnEntity(
    @Id
    var id: UUID,

    // Only carries the foreign key with ON DELETE CASCADE. It is set as a reference and never navigated.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "board_id",
        nullable = false,
        updatable = false,
        foreignKey = ForeignKey(name = "columns_board_id_fk"),
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    var board: BoardEntity,

    @Column(nullable = false, length = 30)
    var name: String,

    @Column(nullable = false)
    var position: Int,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
)
