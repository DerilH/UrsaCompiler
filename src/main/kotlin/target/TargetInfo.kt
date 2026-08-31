package org.derilh.target

import org.derilh.core.PrimitiveTypeKind
import org.derilh.core.TypeInfo
import java.math.BigDecimal
import java.math.BigInteger

abstract class TargetInfo(val types: TargetTypesInfo) {

    fun promoteIntegralType(kind: PrimitiveTypeKind): PrimitiveTypeKind {
        if (!kind.isInt) throw IllegalArgumentException("Trying to promote non-integral type $kind")

        if (kind == PrimitiveTypeKind.CHAR8_T || kind == PrimitiveTypeKind.CHAR16_T || kind == PrimitiveTypeKind.CHAR32_T || kind == PrimitiveTypeKind.WCHAR_T) {
            val promoteTypes = arrayOf(PrimitiveTypeKind.INT, PrimitiveTypeKind.UNSIGNED_INT, PrimitiveTypeKind.LONG, PrimitiveTypeKind.UNSIGNED_LONG, PrimitiveTypeKind.LONG_LONG, PrimitiveTypeKind.UNSIGNED_LONG_LONG)
            val underlyingType = getUnderlyingType(kind)
            return promoteTypes.firstOrNull { canFitInType(underlyingType, it) } ?: underlyingType
        } else if (kind == PrimitiveTypeKind.BOOL) {
            return PrimitiveTypeKind.INT;
        } else if (getRank(kind) < PrimitiveTypeKind.INT.intRank) {
            return if (canFitInType(kind, PrimitiveTypeKind.INT)) {
                PrimitiveTypeKind.INT
            } else {
                PrimitiveTypeKind.UNSIGNED_INT
            }
        } else return kind
    }

    fun canFitInType(kind: PrimitiveTypeKind, fitType: PrimitiveTypeKind): Boolean {
        if (kind == PrimitiveTypeKind.BOOL) return true

        val typeWidth = getBitWidth(kind)
        val fitWidth = getBitWidth(fitType)
        val isSigned = !kind.isUnsigned
        val isFitSigned = !fitType.isUnsigned

        return when {
            isSigned && !isFitSigned -> false
            isSigned == isFitSigned -> typeWidth <= fitWidth
            else -> typeWidth < fitWidth
        }
    }

    fun getBitWidth(kind: PrimitiveTypeKind): Long {
        return getTypeInfo(kind).widthBits
    }

    fun getBitAlign(kind: PrimitiveTypeKind): Long {
        return getTypeInfo(kind).alignBits
    }

    fun getTypeInfo(kind: PrimitiveTypeKind): TypeInfo {
        return when (kind) {
            PrimitiveTypeKind.VOID -> throw IllegalArgumentException("Void type has no width")
            PrimitiveTypeKind.NULLPTR -> types.pointer
            PrimitiveTypeKind.BOOL -> types.bool
            PrimitiveTypeKind.CHAR, PrimitiveTypeKind.SIGNED_CHAR, PrimitiveTypeKind.UNSIGNED_CHAR -> types.char
            PrimitiveTypeKind.CHAR8_T -> types.char8_t
            PrimitiveTypeKind.CHAR16_T -> types.char16_t
            PrimitiveTypeKind.CHAR32_T -> types.char32_t
            PrimitiveTypeKind.WCHAR_T -> types.wchar_t
            PrimitiveTypeKind.SHORT, PrimitiveTypeKind.UNSIGNED_SHORT -> types.short
            PrimitiveTypeKind.INT, PrimitiveTypeKind.UNSIGNED_INT -> types.int
            PrimitiveTypeKind.LONG, PrimitiveTypeKind.UNSIGNED_LONG -> types.long
            PrimitiveTypeKind.LONG_LONG, PrimitiveTypeKind.UNSIGNED_LONG_LONG -> types.longLong
            PrimitiveTypeKind.FLOAT -> types.float
            PrimitiveTypeKind.DOUBLE -> types.double
            PrimitiveTypeKind.LONG_DOUBLE -> types.longDouble
        }
    }

    fun getRank(kind: PrimitiveTypeKind): Int {
        return when (kind) {
            PrimitiveTypeKind.WCHAR_T -> getRank(types.wCharType)
            PrimitiveTypeKind.CHAR16_T -> getRank(types.char16Type)
            PrimitiveTypeKind.CHAR32_T -> getRank(types.char32Type)
            else -> kind.intRank
        }
    }

    fun getUnderlyingType(type: PrimitiveTypeKind): PrimitiveTypeKind {
        return when (type) {
            PrimitiveTypeKind.CHAR8_T -> PrimitiveTypeKind.UNSIGNED_CHAR
            PrimitiveTypeKind.CHAR16_T -> types.char16Type
            PrimitiveTypeKind.CHAR32_T -> types.char32Type
            PrimitiveTypeKind.WCHAR_T -> types.wCharType
            else -> type
        }
    }
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
    val maxInt: BigInteger get() = BigInteger.TWO.pow(int.widthBits.toInt() - 1) - BigInteger.ONE
    val maxUInt: BigInteger get() = BigInteger.TWO.pow(int.widthBits.toInt()) - BigInteger.ONE

    val maxLong: BigInteger get() = BigInteger.TWO.pow(long.widthBits.toInt() - 1) - BigInteger.ONE
    val maxULong: BigInteger get() = BigInteger.TWO.pow(long.widthBits.toInt()) - BigInteger.ONE

    val maxLongLong: BigInteger get() = BigInteger.TWO.pow(longLong.widthBits.toInt() - 1) - BigInteger.ONE
    val maxULongLong: BigInteger get() = BigInteger.TWO.pow(longLong.widthBits.toInt()) - BigInteger.ONE
}