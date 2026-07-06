package com.example.compose_clean_architecture.data.repository_impl

import com.example.compose_clean_architecture.data.data_source.UserRemoteDataSource
import com.example.compose_clean_architecture.domain.entity.UserEntity
import com.example.compose_clean_architecture.domain.repository.UserRepository
import org.koin.core.annotation.Single

@Single
class UserRepositoryImpl(
    private val userRemoteDataSource: UserRemoteDataSource
) : UserRepository {
    override suspend fun getUserList(): List<UserEntity> {
        return userRemoteDataSource.getUserList().map { response ->
            UserEntity(
                name = response.name ?: "",
                email = response.email ?: "",
                address = response.address?.street ?: "",
                city = response.address?.city ?: "",
                latitude = response.address?.geo?.lat?.toDoubleOrNull() ?: 0.0,
                longitude = response.address?.geo?.lng?.toDoubleOrNull() ?: 0.0
            )
        }
    }
}
