package com.plcoding.cardigan.board.domain.exception

import com.plcoding.cardigan.common.domain.exception.UniquenessConflictException

/** The user already has a board with this key. */
class BoardKeyAlreadyUsedException(key: String) : UniquenessConflictException("key", "Board key $key is used")
