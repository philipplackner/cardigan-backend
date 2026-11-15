package com.plcoding.cardigan.user.infra.database.repository

import com.plcoding.cardigan.user.domain.model.User
import com.plcoding.cardigan.user.domain.repository.UserRepository
import com.plcoding.cardigan.user.infra.database.mapper.toUser
import com.plcoding.cardigan.user.infra.database.mapper.toUserEntity
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class JpaUserRepository(
    private val springDataUserRepository: SpringDataUserRepository,
) : UserRepository {

    override fun findById(id: UUID): User? = springDataUserRepository.findByIdOrNull(id)?.toUser()

    override fun existsByEmail(email: String): Boolean = springDataUserRepository.existsByEmail(email)

    override fun save(user: User): User = springDataUserRepository.save(user.toUserEntity()).toUser()
}
