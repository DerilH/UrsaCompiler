package org.derilh.core

enum class PrimitiveTypeKind(val isInt: Boolean = false, val isFloat: Boolean = false, val isUnsigned: Boolean = false, val intRank: Int = -1) {
    VOID,
    NULLPTR,
    BOOL(intRank = 1, isInt = true),

    CHAR(intRank = 2, isInt = true),
    SIGNED_CHAR(intRank = 2, isInt = true),
    UNSIGNED_CHAR(intRank = 2, isUnsigned = true, isInt = true),

    CHAR8_T(isInt = true),
    CHAR16_T(isInt = true),
    CHAR32_T(isInt = true),
    WCHAR_T(isInt = true),
    SHORT(intRank = 3, isInt = true),
    UNSIGNED_SHORT(intRank = 3, isInt = true, isUnsigned = true),
    INT(intRank = 4, isInt = true),
    UNSIGNED_INT(intRank = 4, isInt = true, isUnsigned = true),
    LONG(intRank = 5, isInt = true),
    UNSIGNED_LONG(intRank = 5, isInt = true, isUnsigned = true),
    LONG_LONG(intRank = 6, isInt = true),
    UNSIGNED_LONG_LONG(intRank = 6, isInt = true, isUnsigned = true),

    FLOAT(isFloat = true),
    DOUBLE(isFloat = true),
    LONG_DOUBLE(isFloat = true),
}

data class TypeInfo(
    val widthBits: Int,
    val alignBits: Int
)