package org.derilh.core

enum class ValueCategory {
    LVALUE,
    PRVALUE,
    XVALUE;

    val isGLValue: Boolean
        get() = this == LVALUE || this == XVALUE

    val isRValue: Boolean
        get() = this == PRVALUE || this == XVALUE
}