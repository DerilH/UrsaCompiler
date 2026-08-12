package org.derilh.core

enum class PrimitiveTypeKind(val isInt: Boolean = false, val isUnsigned: Boolean = false) {
    VOID,
    BOOL,

    CHAR,
    SIGNED_CHAR,
    UNSIGNED_CHAR(isUnsigned = true),

    CHAR8_T,
    CHAR16_T,
    CHAR32_T,
    WCHAR_T,
    SHORT(isInt = true),
    UNSIGNED_SHORT(isInt = true, isUnsigned = true),
    INT(isInt = true),
    UNSIGNED_INT(isInt = true, isUnsigned = true),
    LONG(isInt = true),
    UNSIGNED_LONG(isInt = true, isUnsigned = true),
    LONG_LONG(isInt = true),
    UNSIGNED_LONG_LONG(isInt = true, isUnsigned = true),

    FLOAT,
    DOUBLE,
    LONG_DOUBLE
}

data class TypeInfo(
    val widthBits: Int,
    val alignBits: Int
)