package com.example.compose_clean_architecture.presentation.screens.user.viewModel

import com.example.compose_clean_architecture.core.state_status.BaseStatus
import com.example.compose_clean_architecture.domain.entity.UserEntity

data class UserListState(
    val status: BaseStatus<List<UserEntity>> = BaseStatus.Initial
)