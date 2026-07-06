package com.example.compose_clean_architecture.core.state_status

import com.example.compose_clean_architecture.core.error.ResponseError

sealed class BaseStatus<out T> {
    object Initial : BaseStatus<Nothing>()
    object Loading : BaseStatus<Nothing>()
    data class Success<T>(val data: T) : BaseStatus<T>()
    object Valid : BaseStatus<Nothing>()
    object Invalid : BaseStatus<Nothing>()
    object ButtonClicked : BaseStatus<Nothing>()
    data class Failure(val error: ResponseError) : BaseStatus<Nothing>()

    val isInitial: Boolean get() = this is Initial
    val isLoading: Boolean get() = this is Loading
    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure
    val isValid: Boolean get() = this is Valid
    val isInvalid: Boolean get() = this is Invalid
    val isButtonClicked: Boolean get() = this is ButtonClicked

    fun getOrNull(): T? = if (this is Success) data else null
}
