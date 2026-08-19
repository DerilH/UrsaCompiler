package org.derilh.core

sealed interface OpResult<out T> {
    class Success<T>(val value: T) : OpResult<T>
    class Failure(val message: String, val args: List<Any>) : OpResult<Nothing>

    companion object {
        fun <T> success(value: T): Success<T> = Success(value)
        fun failure(message: String, vararg args: Any): Failure = Failure(message, args.toList())
    }

}

inline fun <reified T> OpResult<T>.getOrElse(onFailure: (OpResult.Failure) -> Nothing): T = when (this) {
    is OpResult.Success<T> -> value
    is OpResult.Failure -> onFailure(this)
}

inline fun <reified T> OpResult<T>.ifFailure(onFailure: (OpResult.Failure) -> Unit) = when (this) {
    is OpResult.Failure -> onFailure(this)
    else -> {}
}