package com.plcoding.cardigan.board.infra.database.mapper

import com.plcoding.cardigan.board.domain.model.Board
import com.plcoding.cardigan.board.domain.model.Column
import com.plcoding.cardigan.board.infra.database.entity.BoardEntity
import com.plcoding.cardigan.board.infra.database.entity.ColumnEntity

fun BoardEntity.toBoard(): Board =
    Board(
        id = id,
        ownerId = ownerId,
        name = name,
        key = key,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun Board.toBoardEntity(): BoardEntity =
    BoardEntity(
        id = id,
        ownerId = ownerId,
        name = name,
        key = key,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun ColumnEntity.toColumn(): Column =
    Column(
        id = id,
        boardId = board.id,
        name = name,
        position = position,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

/** [board] is a reference to the column's board, which only supplies the foreign key. */
fun Column.toColumnEntity(board: BoardEntity): ColumnEntity =
    ColumnEntity(
        id = id,
        board = board,
        name = name,
        position = position,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
