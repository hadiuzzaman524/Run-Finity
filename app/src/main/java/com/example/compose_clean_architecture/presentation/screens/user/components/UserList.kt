package com.example.compose_clean_architecture.presentation.screens.user.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.compose_clean_architecture.core.constants.AppConstant
import com.example.compose_clean_architecture.domain.entity.UserEntity
import com.example.compose_clean_architecture.presentation.components.AppText

@Composable
fun UserList(
    users: List<UserEntity>,
    columns: Int = 1
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        contentPadding = PaddingValues(AppConstant.horizontalGap16),
        modifier = Modifier.fillMaxSize()
    ) {
        items(users) { user ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppConstant.horizontalGap8),
                shape = RoundedCornerShape(AppConstant.borderRadius12)
            ) {
                Column(modifier = Modifier.padding(AppConstant.horizontalGap16)) {
                    AppText.TitleMedium(user.name)
                    AppText.BodyLarge(user.email)
                    AppText.BodyLarge(
                        "${user.city}, ${user.address}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
