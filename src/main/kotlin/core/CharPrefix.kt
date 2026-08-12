package org.derilh.core

enum class CharPrefix(val prefix: String) {
    NONE(""),   // 'a'   -> char
    UTF8("u8"),   // u8'a' -> char8_t
    UTF16("u"),  // u'a'  -> char16_t
    UTF32("U"),  // U'a'  -> char32_t
    WIDE("L")    // L'a'  -> wchar_t
}

fun CharPrefix.toType(): PrimitiveTypeKind {
    return when (this) {
        CharPrefix.NONE  -> PrimitiveTypeKind.CHAR
        CharPrefix.UTF8  -> PrimitiveTypeKind.CHAR8_T
        CharPrefix.UTF16 -> PrimitiveTypeKind.CHAR16_T
        CharPrefix.UTF32 -> PrimitiveTypeKind.CHAR32_T
        CharPrefix.WIDE  -> PrimitiveTypeKind.WCHAR_T
    }
}

