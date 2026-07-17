package com.example.weatherrecommender.domain.model

/**
 * Represents all known errors that can occur within the application's domain.
 */
sealed interface AppError {
    /** Errors originating from the network layer (e.g. connectivity, timeouts). */
    sealed interface NetworkError : AppError {
        data object NoConnectivity : NetworkError
        data object Timeout : NetworkError
        data class ServerError(val code: Int) : NetworkError
        data class Unknown(val throwable: Throwable) : NetworkError
    }
    
    /** Errors originating from the API (e.g. rate limiting). */
    sealed interface ApiError : AppError {
        data object RateLimitExceeded : ApiError
        data object NotFound : ApiError
    }

    /** Catch-all for unexpected domain or unhandled layer errors. */
    data class Unknown(val throwable: Throwable) : AppError
}

/** Convenience type alias for domain results. */
typealias AppResult<T> = Result<T, AppError>

/**
 * A generic Monadic Result type for explicit state modeling.
 *
 * Forces the caller to handle both the Success and Error states explicitly,
 * preventing unhandled exceptions and improving robustness.
 */
sealed interface Result<out T, out E> {
    data class Success<out T>(val data: T) : Result<T, Nothing>
    data class Error<out E>(val error: E) : Result<Nothing, E>
    
    /** Transforms the successful data payload while preserving the error state. */
    fun <R> map(transform: (T) -> R): Result<R, E> {
        return when (this) {
            is Success -> Success(transform(data))
            is Error -> Error(error)
        }
    }
    
    /** Executes side-effects for both states. */
    fun fold(onSuccess: (T) -> Unit, onError: (E) -> Unit) {
        when (this) {
            is Success -> onSuccess(data)
            is Error -> onError(error)
        }
    }
}
