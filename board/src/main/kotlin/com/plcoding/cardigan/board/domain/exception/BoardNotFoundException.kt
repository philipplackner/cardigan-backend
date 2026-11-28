package com.plcoding.cardigan.board.domain.exception

import com.plcoding.cardigan.common.domain.exception.ResourceNotFoundException
import java.util.UUID

/** No board with this ID exists for the user. A board of another user counts as missing. */
class BoardNotFoundException(
    boardId: UUID,
) : ResourceNotFoundException("No board $boardId")
