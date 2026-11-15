package com.plcoding.cardigan.user.infra.database.repository

import com.plcoding.cardigan.user.infra.database.entity.UserEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SpringDataUserRepository : JpaRepository<UserEntity, UUID> {

    fun existsByEmail(email: String): Boolean
}
