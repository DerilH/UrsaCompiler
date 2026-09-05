package org.derilh.core

enum class PrimitiveTypeKind(val isInt: Boolean = false, val isFloat: Boolean = false, val isUnsigned: Boolean = false, val intRank: Int = -1, val floatRank: Int = -1, val floatSubRank: Int = -1) {
    VOID,
    NULLPTR,
    BOOL(intRank = 1, isInt = true),

    CHAR(intRank = 2, isInt = true),
    SIGNED_CHAR(intRank = 2, isInt = true),
    UNSIGNED_CHAR(intRank = 2, isUnsigned = true, isInt = true),

    CHAR8_T(isInt = true, isUnsigned = true),
    CHAR16_T(isInt = true, isUnsigned = true),
    CHAR32_T(isInt = true, isUnsigned = true),
    WCHAR_T(isInt = true),
    SHORT(intRank = 3, isInt = true),
    UNSIGNED_SHORT(intRank = 3, isInt = true, isUnsigned = true),
    INT(intRank = 4, isInt = true),
    UNSIGNED_INT(intRank = 4, isInt = true, isUnsigned = true),
    LONG(intRank = 5, isInt = true),
    UNSIGNED_LONG(intRank = 5, isInt = true, isUnsigned = true),
    LONG_LONG(intRank = 6, isInt = true),
    UNSIGNED_LONG_LONG(intRank = 6, isInt = true, isUnsigned = true),

    FLOAT(isFloat = true, floatRank = 20, floatSubRank = 1),
    DOUBLE(isFloat = true, floatRank = 30, floatSubRank = 1),
    LONG_DOUBLE(isFloat = true, floatRank = 40, floatSubRank = 1);

    fun toUnsigned(): PrimitiveTypeKind = when (this) {
        SHORT -> UNSIGNED_SHORT
        INT -> UNSIGNED_INT
        LONG -> UNSIGNED_LONG
        LONG_LONG -> UNSIGNED_LONG_LONG
        CHAR, SIGNED_CHAR -> UNSIGNED_CHAR
        else -> this
    }


    val cName: String
        get() = when (this) {
            VOID -> "void"
            NULLPTR -> "std::nullptr_t"
            BOOL -> "bool"

            CHAR -> "char"
            SIGNED_CHAR -> "signed char"
            UNSIGNED_CHAR -> "unsigned char"

            CHAR8_T -> "char8_t"
            CHAR16_T -> "char16_t"
            CHAR32_T -> "char32_t"
            WCHAR_T -> "wchar_t"

            SHORT -> "short"
            UNSIGNED_SHORT -> "unsigned short"
            INT -> "int"
            UNSIGNED_INT -> "unsigned int"
            LONG -> "long"
            UNSIGNED_LONG -> "unsigned long"
            LONG_LONG -> "long long"
            UNSIGNED_LONG_LONG -> "unsigned long long"

            FLOAT -> "float"
            DOUBLE -> "double"
            LONG_DOUBLE -> "long double"
        }
}

data class TypeInfo(val widthBits: Long, val alignBits: Long)
