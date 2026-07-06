package com.example.compose_clean_architecture.presentation.screens.user

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.koin.koinScreenModel
import com.example.compose_clean_architecture.core.utils.AppScreen
import com.example.compose_clean_architecture.core.error.ErrorLocalization
import com.example.compose_clean_architecture.core.utils.PreviewAllDevices
import com.example.compose_clean_architecture.core.state_status.BaseStatus
import com.example.compose_clean_architecture.core.utils.DeviceType
import com.example.compose_clean_architecture.core.utils.getDeviceType
import com.example.compose_clean_architecture.domain.entity.UserEntity
import com.example.compose_clean_architecture.presentation.components.AppText
import com.example.compose_clean_architecture.presentation.screens.user.components.UserList
import com.example.compose_clean_architecture.presentation.screens.user.viewModel.UserListState
import com.example.compose_clean_architecture.presentation.screens.user.viewModel.UserListViewModel
import com.example.compose_clean_architecture.ui.theme.ComposecleanarchitectureTheme

class UserListScreen : AppScreen {

    @Composable
    override fun BuildCompact() {
        UserListLayout(columns = 1)
    }

    @Composable
    override fun BuildTablet() {
        UserListLayout(columns = 2)
    }

    @Composable
    override fun BuildDesktop() {
        UserListLayout(columns = 3)
    }

    @Composable
    private fun UserListLayout(columns: Int) {
        val viewModel: UserListViewModel = koinScreenModel()
        val state by viewModel.state.collectAsState()

        UserListContent(state = state, columns = columns)

        LaunchedEffect(Unit) {
            viewModel.loadUsers()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserListContent(state: UserListState, columns: Int) {
    Scaffold(
        topBar = {
            TopAppBar(title = {
                AppText.TitleMedium("User List")
            })
        }) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when (val status = state.status) {
                is BaseStatus.Loading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )

                is BaseStatus.Failure -> AppText.BodyLarge(
                    ErrorLocalization.getString(status.error),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center)
                )

                is BaseStatus.Success -> {
                    val users = status.data as? List<UserEntity>
                    if (users != null) {
                        UserList(users = users, columns = columns)
                    }
                }

                else -> {}
            }
        }
    }
}

@PreviewAllDevices
@Composable
private fun UserListScreenPreview() {
    val deviceType = getDeviceType()
    val columns = when (deviceType) {
        DeviceType.MOBILE -> 1
        DeviceType.TABLET -> 2
        DeviceType.DESKTOP -> 3
    }

    ComposecleanarchitectureTheme {
        UserListContent(
            state = UserListState(
                status = BaseStatus.Success(
                    listOf(
                        UserEntity(
                            name = "John Doe",
                            email = "john@example.com",
                            address = "123 St",
                            city = "New York",
                            latitude = 40.7128,
                            longitude = -74.0060
                        ),
                        UserEntity(
                            name = "Jane Smith",
                            email = "jane@example.com",
                            address = "456 Ave",
                            city = "Los Angeles",
                            latitude = 34.0522,
                            longitude = -118.2437
                        ),
                        UserEntity(
                            name = "Bob Wilson",
                            email = "bob@example.com",
                            address = "789 Blvd",
                            city = "Chicago",
                            latitude = 41.8781,
                            longitude = -87.6298
                        )
                    )
                )
            ),
            columns = columns
        )
    }
}
