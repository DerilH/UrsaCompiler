package org.derilh.core

enum class RefQualifier {
    NONE,   //
    LVALUE, // &
    RVALUE  // &&
};

data class MethodQualifiers (
    val isConst: Boolean = false,
    val isVolatile: Boolean = false,
    val refQualifier: RefQualifier= RefQualifier.NONE,
    val isNoExcept: Boolean = false
)