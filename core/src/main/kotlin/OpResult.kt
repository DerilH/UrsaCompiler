package org.derilh.core

import org.derilh.core.OpResult.Failure
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

sealed interface OpResult<out T> {
    class Success<T>(val value: T) : OpResult<T>
    class Failure(val message: String, val args: List<Any>) : OpResult<Nothing>


    companion object {
        fun <T> success(value: T): Success<T> = Success(value)
        fun failure(message: String, vararg args: Any): Failure = Failure(message, args.toList())
    }


}


@OptIn(ExperimentalContracts::class)
fun <T> OpResult<T>.isSuccess(): Boolean {
    contract { returns(true) implies (this@isSuccess is OpResult.Success<T>) }
    return this is OpResult.Success<T>
}

@OptIn(ExperimentalContracts::class)
fun <T> OpResult<T>.isFailure(): Boolean {
    contract { returns(true) implies (this@isFailure is Failure) }
    return this is Failure
}

inline fun <reified T> OpResult<T>.orElse(onFailure: (OpResult.Failure) -> Nothing): OpResult<T> = when (this) {
    is OpResult.Success<T> -> this
    is Failure -> onFailure(this)
}

@OptIn(ExperimentalContracts::class)
inline fun <T, reified C : T> OpResult<T>.getAsOrElse(onFailure: (Failure) -> C): C {
    contract {
        callsInPlace(onFailure, InvocationKind.AT_MOST_ONCE)
    }
    return when (this) {
        is OpResult.Success -> {
            val v = this.value
            v as? C ?: onFailure(OpResult.failure("Expected type ${C::class.simpleName}, but got ${if(v == null) null else v::class.simpleName}"))
        }
        is Failure -> onFailure(this)
    }
}

@OptIn(ExperimentalContracts::class)
inline fun <reified R, T : R> OpResult<T>.getOrElse(onFailure: (Failure) -> R): R {
    contract {
        callsInPlace(onFailure, InvocationKind.AT_MOST_ONCE)
    }
    return when(this) {
        is OpResult.Success<T> -> value
        is Failure -> onFailure(this)
    }
}

fun <T> OpResult<T>.getOrNull(): T? = when (this) {
    is OpResult.Success<T> -> value
    is Failure -> null;
}

inline fun <reified T> OpResult<*>.getAsOrNull(): T? {
    return when (this) {
        is OpResult.Success -> {
            value as? T
        }
        is Failure -> null;
    }
}

inline fun <reified T> OpResult<T>.ifFailure(onFailure: (OpResult.Failure) -> Unit) = when (this) {
    is Failure -> onFailure(this)
    else -> {}
}

val <T> T.asSuccess: OpResult.Success<T>
    get() = OpResult.Success(this)