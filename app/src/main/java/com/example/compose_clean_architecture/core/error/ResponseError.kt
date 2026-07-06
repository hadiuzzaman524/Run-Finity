package com.example.compose_clean_architecture.core.error

import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

sealed class ResponseError : Exception() {
    data object BadResponse : ResponseError()
    data object NotFound : ResponseError()
    data object UnAuthorized : ResponseError()
    data object NoInternetConnection : ResponseError()
    data object UnExpected : ResponseError()
    data object SendTimeOut : ResponseError()
    data object ConnectionTimeOut : ResponseError()
    data object ReceiveTimeOut : ResponseError()
    data object Cancel : ResponseError()
    data object Unknown : ResponseError()
    data object Conflict : ResponseError()
    data object UnProcessable : ResponseError()
    data object TooManyRequest : ResponseError()
    data object InternalServerError : ResponseError()
    data object BadGetWay : ResponseError()
    data class ErrorWithMessage(val errorMessage: String) : ResponseError()

    companion object {
        fun from(throwable: Throwable): ResponseError {
            return when (throwable) {
                is ResponseError -> throwable
                is HttpException -> {
                    when (throwable.code()) {
                        400 -> BadResponse
                        401 -> UnAuthorized
                        404 -> NotFound
                        409 -> Conflict
                        422 -> UnProcessable
                        429 -> TooManyRequest
                        500 -> InternalServerError
                        502 -> BadGetWay
                        else -> UnExpected
                    }
                }
                is SocketTimeoutException -> ConnectionTimeOut
                is IOException -> NoInternetConnection
                else -> throwable.message?.let { ErrorWithMessage(it) } ?: UnExpected
            }
        }
    }
}
