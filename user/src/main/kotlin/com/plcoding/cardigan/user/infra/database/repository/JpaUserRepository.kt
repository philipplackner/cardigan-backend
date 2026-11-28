package com.plcoding.cardigan.user.infra.database.repository

import com.plcoding.cardigan.user.domain.exception.EmailAlreadyUsedException
import com.plcoding.cardigan.user.domain.model.User
import com.plcoding.cardigan.user.domain.repository.UserRepository
import com.plcoding.cardigan.user.infra.database.entity.UserEntity
import com.plcoding.cardigan.user.infra.database.mapper.toUser
import com.plcoding.cardigan.user.infra.database.mapper.toUserEntity
import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class JpaUserRepository(
    private val springDataUserRepository: SpringDataUserRepository,
) : UserRepository {
    override fun findById(id: UUID): User? = springDataUserRepository.findByIdOrNull(id)?.toUser()

    override fun findByEmail(email: String): User? = springDataUserRepository.findByEmail(email)?.toUser()

    override fun existsByEmail(email: String): Boolean = springDataUserRepository.existsByEmail(email)

    // Flushes, so a duplicate email fails here and not at commit, where it couldn't be told apart.
    override fun save(user: User): User =
        try {
            springDataUserRepository.saveAndFlush(user.toUserEntity()).toUser()
        } catch (e: DataIntegrityViolationException) {
            if (e.violatesEmailUniqueness()) throw EmailAlreadyUsedException() else throw e
        }

    private fun Throwable.violatesEmailUniqueness(): Boolean =
        generateSequence(this) { it.cause?.takeIf { cause -> cause !== it } }
            .filterIsInstance<ConstraintViolationException>()
            .any { it.constraintName?.substringAfterLast('.') == UserEntity.EMAIL_UNIQUE_CONSTRAINT }
}
