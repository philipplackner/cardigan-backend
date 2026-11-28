package com.plcoding.cardigan.board.domain.exception

import com.plcoding.cardigan.common.domain.exception.ResourceNotFoundException
import java.util.UUID

/** The label doesn't exist or belongs to another user. */
class LabelNotFoundException(
    labelId: UUID,
) : ResourceNotFoundException("No label $labelId")
