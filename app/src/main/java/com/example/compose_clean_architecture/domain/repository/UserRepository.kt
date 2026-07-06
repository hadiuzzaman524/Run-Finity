package com.example.compose_clean_architecture.domain.repository

import com.example.compose_clean_architecture.domain.entity.UserEntity

interface UserRepository {
    suspend fun getUserList(): List<UserEntity>
}
