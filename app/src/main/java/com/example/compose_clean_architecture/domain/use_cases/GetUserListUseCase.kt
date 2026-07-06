package com.example.compose_clean_architecture.domain.use_cases

import com.example.compose_clean_architecture.domain.entity.UserEntity
import com.example.compose_clean_architecture.domain.repository.UserRepository
import org.koin.core.annotation.Single

@Single
class GetUserListUseCase(
    private val userRepository: UserRepository
) {
    suspend fun execute(): List<UserEntity> = userRepository.getUserList()
}
