package com.plcoding.cardigan.board.domain.exception

import com.plcoding.cardigan.common.domain.exception.ResourceNotFoundException
import java.util.UUID

/** The board has no column with this ID. A column of another board counts as missing. */
class ColumnNotFoundException(
    columnId: UUID,
) : ResourceNotFoundException("No column $columnId")
