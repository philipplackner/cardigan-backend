package com.plcoding.cardigan.user.domain.exception

import com.plcoding.cardigan.common.domain.exception.UniquenessConflictException

class EmailAlreadyUsedException : UniquenessConflictException("email", "The email is already used")
