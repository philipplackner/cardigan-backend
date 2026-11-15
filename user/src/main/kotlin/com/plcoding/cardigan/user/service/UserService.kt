package com.plcoding.cardigan.user.service

import com.plcoding.cardigan.user.domain.exception.CurrentUserNotFoundException
import com.plcoding.cardigan.user.domain.model.User
import com.plcoding.cardigan.user.domain.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class UserService(
    private val userRepository: UserRepository,
) {

    @Transactional(readOnly = true)
    fun getCurrentUser(userId: UUID): User =
        userRepository.findById(userId) ?: throw CurrentUserNotFoundException(userId)
}
