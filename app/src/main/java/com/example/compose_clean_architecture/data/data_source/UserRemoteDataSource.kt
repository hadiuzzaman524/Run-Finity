package com.example.compose_clean_architecture.data.data_source

import com.example.compose_clean_architecture.data.models.UserResponseModel
import retrofit2.http.GET

interface UserRemoteDataSource {
    @GET("/users")
    suspend fun getUserList(): List<UserResponseModel>
}
