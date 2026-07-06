package com.example.compose_clean_architecture.core.error

object ErrorLocalization {
    fun getString(error: ResponseError): String {
        return when (error) {
            is ResponseError.BadResponse -> "Bad request. Please check your input and try again."
            is ResponseError.NotFound -> "Resource not found."
            is ResponseError.UnAuthorized -> "Unauthorized access. Please check your credentials."
            is ResponseError.NoInternetConnection -> "No internet connection. Please check your network and try again."
            is ResponseError.UnExpected -> "An unexpected error occurred. Please try again later."
            is ResponseError.SendTimeOut -> "Request timeout. Please try again."
            is ResponseError.ConnectionTimeOut -> "Connection timed out. Please check your network."
            is ResponseError.ReceiveTimeOut -> "Response timeout. Please try again."
            is ResponseError.Cancel -> "Request was cancelled."
            is ResponseError.Unknown -> "An unknown error occurred. Please try again."
            is ResponseError.Conflict -> "Conflict detected. This email is already in use."
            is ResponseError.UnProcessable -> "Request could not be processed. Please verify your data."
            is ResponseError.TooManyRequest -> "Too many requests. Please wait and try again later."
            is ResponseError.InternalServerError -> "Internal server error. Please try again later."
            is ResponseError.BadGetWay -> "Bad gateway. Please try again later."
            is ResponseError.ErrorWithMessage -> error.errorMessage
        }
    }
}
