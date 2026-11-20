package com.plcoding.cardigan.board.domain.exception

import com.plcoding.cardigan.common.domain.exception.UniquenessConflictException

/** Another label of the same user has the same name after case folding. */
class LabelNameAlreadyUsedException : UniquenessConflictException("name", "Label name is already used")
