package com.example.compose_clean_architecture.domain.entity

data class UserEntity(
    val name: String,
    val email: String,
    val address: String,
    val city: String,
    val latitude: Double,
    val longitude: Double,
)
