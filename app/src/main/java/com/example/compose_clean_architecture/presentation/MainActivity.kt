package com.example.compose_clean_architecture.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import cafe.adriel.voyager.navigator.Navigator
import com.example.compose_clean_architecture.presentation.screens.user.UserListScreen
import com.example.compose_clean_architecture.ui.theme.ComposecleanarchitectureTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ComposecleanarchitectureTheme {
                Navigator(UserListScreen())
            }
        }
    }
}