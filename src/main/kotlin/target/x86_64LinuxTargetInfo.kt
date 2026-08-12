package org.derilh.target

import org.derilh.core.PrimitiveTypeKind
import org.derilh.core.TypeInfo
import java.math.BigDecimal

private val types = TargetTypesInfo(
    isCharSigned = true,
    bool = TypeInfo(8, 8),
    char = TypeInfo(8, 8),
    char8_t = TypeInfo(8,8),
    char16_t = TypeInfo(16,16),
    char32_t = TypeInfo(32,32),
    wchar_t = TypeInfo(32,32),
    short = TypeInfo(16, 16),
    int = TypeInfo(32, 32),
    long = TypeInfo(64, 64),
    longLong = TypeInfo(64, 64),
    float = TypeInfo(32, 32),
    double = TypeInfo(64, 64),
    longDouble = TypeInfo(128, 128),
    pointer = TypeInfo(64, 64),

    sizeType = PrimitiveTypeKind.UNSIGNED_LONG,
    ptrDiffType = PrimitiveTypeKind.LONG,
    intMaxType = PrimitiveTypeKind.LONG_LONG,
    uIntMaxType = PrimitiveTypeKind.UNSIGNED_LONG_LONG,
    intPtrType = PrimitiveTypeKind.LONG,
    uIntPtrType = PrimitiveTypeKind.UNSIGNED_LONG,
    wCharType = PrimitiveTypeKind.INT,
    wIntType = PrimitiveTypeKind.UNSIGNED_INT,
    char16Type = PrimitiveTypeKind.UNSIGNED_SHORT,
    char32Type = PrimitiveTypeKind.UNSIGNED_INT,
    int8Type = PrimitiveTypeKind.SIGNED_CHAR,
    int16Type = PrimitiveTypeKind.SHORT,
    int32Type = PrimitiveTypeKind.INT,
    int64Type = PrimitiveTypeKind.LONG,
    sigAtomicType = PrimitiveTypeKind.INT,
    processIdType = PrimitiveTypeKind.INT,
    maxFloat = BigDecimal("3.4028235E38"),
    maxDouble = BigDecimal("1.7976931348623157E308"),
    maxLongDouble = BigDecimal("1.189731495357231765085759326628007016196477e4932")
)

class X86_64LinuxTargetInfo : TargetInfo(types = types
)