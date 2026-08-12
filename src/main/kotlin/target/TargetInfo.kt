package org.derilh.target

import org.derilh.core.PrimitiveTypeKind
import org.derilh.core.TypeInfo
import java.math.BigDecimal
import java.math.BigInteger

abstract class TargetInfo(val types: TargetTypesInfo) {
}

data class TargetTypesInfo(
    val isCharSigned: Boolean,
    val bool: TypeInfo,
    val char: TypeInfo,
    val char8_t: TypeInfo,
    val char16_t: TypeInfo,
    val char32_t: TypeInfo,
    val wchar_t: TypeInfo,
    val short: TypeInfo,
    val int: TypeInfo,
    val long: TypeInfo,
    val longLong: TypeInfo,
    val float: TypeInfo,
    val double: TypeInfo,
    val longDouble: TypeInfo,
    val pointer: TypeInfo,

    // System and BuiltinTypes
    val sizeType: PrimitiveTypeKind,
    val ptrDiffType: PrimitiveTypeKind,
    val intMaxType: PrimitiveTypeKind,
    val uIntMaxType: PrimitiveTypeKind,
    val intPtrType: PrimitiveTypeKind,
    val uIntPtrType: PrimitiveTypeKind,
    val wCharType: PrimitiveTypeKind,
    val wIntType: PrimitiveTypeKind,
    val char16Type: PrimitiveTypeKind,
    val char32Type: PrimitiveTypeKind,
    val int8Type: PrimitiveTypeKind,
    val int16Type: PrimitiveTypeKind,
    val int32Type: PrimitiveTypeKind,
    val int64Type: PrimitiveTypeKind,
    val sigAtomicType: PrimitiveTypeKind,
    val processIdType: PrimitiveTypeKind,

    val maxFloat: BigDecimal,
    val maxDouble: BigDecimal,
    val maxLongDouble: BigDecimal
) {
    val maxInt: BigInteger get() = BigInteger.TWO.pow(int.widthBits - 1) - BigInteger.ONE
    val maxUInt: BigInteger get() = BigInteger.TWO.pow(int.widthBits) - BigInteger.ONE

    val maxLong: BigInteger get() = BigInteger.TWO.pow(long.widthBits - 1) - BigInteger.ONE
    val maxULong: BigInteger get() = BigInteger.TWO.pow(long.widthBits) - BigInteger.ONE

    val maxLongLong: BigInteger get() = BigInteger.TWO.pow(longLong.widthBits - 1) - BigInteger.ONE
    val maxULongLong: BigInteger get() = BigInteger.TWO.pow(longLong.widthBits) - BigInteger.ONE


}