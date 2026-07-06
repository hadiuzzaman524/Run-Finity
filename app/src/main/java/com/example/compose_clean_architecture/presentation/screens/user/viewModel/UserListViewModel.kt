package com.example.compose_clean_architecture.presentation.screens.user.viewModel

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.example.compose_clean_architecture.core.error.ResponseError
import com.example.compose_clean_architecture.core.state_status.BaseStatus
import com.example.compose_clean_architecture.domain.use_cases.GetUserListUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.Factory

@Factory
class UserListViewModel(
    private val getUserListUseCase: GetUserListUseCase
) : ScreenModel {

    private val _state = MutableStateFlow(UserListState())
    val state: StateFlow<UserListState> = _state.asStateFlow()

    fun loadUsers() {
        screenModelScope.launch {
            _state.update { it.copy(status = BaseStatus.Loading) }
            try {
                val users = getUserListUseCase.execute()
                _state.update { it.copy(status = BaseStatus.Success(users)) }
            } catch (e: Exception) {
                _state.update { it.copy(status = BaseStatus.Failure(ResponseError.from(e))) }
            }
        }
    }
}
