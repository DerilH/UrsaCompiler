package org.derilh.semantic

import org.derilh.analyzer.DeclSymbol
import org.derilh.core.FunctionQualifiers
import org.derilh.core.PrimitiveTypeKind

class TypeContext(val sizeT: PrimitiveTypeKind, val ptrDiffT: PrimitiveTypeKind) {
    private object PrivateToken

    class Key private constructor(
        private val context: TypeContext,
        @Suppress("UNUSED_PARAMETER") token: PrivateToken
    ) {
        companion object {
            internal fun create(context: TypeContext) = Key(context, PrivateToken)
        }
        fun getOrCreate(elementType: SemanticType): SemanticType = context.intern(elementType)
    }

    private val key: Key = Key.create(this)

    private val typePool = HashMap<SemanticType, SemanticType>()
    val nullptr = getPrimitive(PrimitiveTypeKind.NULLPTR)

    val void = getPrimitive(PrimitiveTypeKind.VOID)
    val bool = getPrimitive(PrimitiveTypeKind.BOOL)
    val int = getPrimitive(PrimitiveTypeKind.INT)
    val uInt = getPrimitive(PrimitiveTypeKind.UNSIGNED_INT)
    val char = getPrimitive(PrimitiveTypeKind.CHAR)
    val sChar = getPrimitive(PrimitiveTypeKind.SIGNED_CHAR)
    val uChar = getPrimitive(PrimitiveTypeKind.UNSIGNED_CHAR)
    val short = getPrimitive(PrimitiveTypeKind.SHORT)
    val uShort = getPrimitive(PrimitiveTypeKind.UNSIGNED_SHORT)
    val long = getPrimitive(PrimitiveTypeKind.LONG)
    val uLong = getPrimitive(PrimitiveTypeKind.UNSIGNED_LONG)
    val longLong = getPrimitive(PrimitiveTypeKind.LONG_LONG)
    val uLongLong = getPrimitive(PrimitiveTypeKind.UNSIGNED_LONG_LONG)
    val float = getPrimitive(PrimitiveTypeKind.FLOAT)
    val double = getPrimitive(PrimitiveTypeKind.DOUBLE)
    val longDouble = getPrimitive(PrimitiveTypeKind.LONG_DOUBLE)
    val char8_t = getPrimitive(PrimitiveTypeKind.CHAR8_T)
    val char16_t = getPrimitive(PrimitiveTypeKind.CHAR16_T)
    val char32_t = getPrimitive(PrimitiveTypeKind.CHAR32_T)
    val wchar_t = getPrimitive(PrimitiveTypeKind.WCHAR_T)

    val size_t = getPrimitive(sizeT)
    val ptrDiff_t = getPrimitive(ptrDiffT)

    val auto = intern(SemanticType.Auto(key = key))
    val voidPtr = getPointer(void)

    private fun <T : SemanticType> intern(type: T): T {
        @Suppress("UNCHECKED_CAST")
        return typePool.getOrPut(type) { type } as T
    }

    fun getPrimitive(kind: PrimitiveTypeKind, isConst: Boolean = false, isVolatile: Boolean = false): SemanticType.Primitive {
        return intern(SemanticType.Primitive(kind, isConst, isVolatile, key))
    }

    fun getPointer(pointee: SemanticType, isConst: Boolean = false, isVolatile: Boolean = false): SemanticType.Pointer {
        return intern(SemanticType.Pointer(pointee, isConst, isVolatile, key))
    }

    fun getReference(pointee: SemanticType): SemanticType.Reference {
        return intern(SemanticType.Reference(pointee, key))
    }

    fun getRValueReference(pointee: SemanticType): SemanticType.RValueReference {
        return intern(SemanticType.RValueReference(pointee, key))
    }

    fun getArray(elementType: SemanticType, size: Long?): SemanticType.Array {
        return intern(SemanticType.Array(elementType, size, key))
    }

    fun getDeclared(classDecl: DeclSymbol.ClassDecl, isConst: Boolean = false, isVolatile: Boolean = false): SemanticType.Declared {
        return intern(SemanticType.Declared(classDecl,isConst,isVolatile, key))
    }

    fun getMemberPointer(classDecl: DeclSymbol.ClassDecl, pointee: SemanticType, isConst: Boolean = false, isVolatile: Boolean = false): SemanticType.MemberPointer {
        return intern(SemanticType.MemberPointer(classDecl, pointee, isConst,isVolatile, key))
    }

    fun getFunction(returnType: SemanticType, params: List<SemanticType>, qualifiers: FunctionQualifiers): SemanticType.Function {
        return intern(SemanticType.Function(returnType, params.map { dropCV(decay(it)) }, qualifiers, key))
    }
    fun getBoundMethod(classDecl: DeclSymbol.ClassDecl, function: SemanticType.Function, isConst: Boolean = false, isVolatile: Boolean = false): SemanticType.BoundMethod {
        return intern(SemanticType.BoundMethod(function, classDecl, key))
    }
    fun getAuto(isConst: Boolean, isVolatile: Boolean): SemanticType.Auto {
        return intern(SemanticType.Auto(isConst, isVolatile, key))
    }

    fun dropCV(type: SemanticType): SemanticType = type.dropCV(key)
    fun addCV(type: SemanticType, isConst: Boolean, isVolatile: Boolean): SemanticType = type.addCV(isConst, isVolatile, key)
    fun removeRef(type: SemanticType): SemanticType = type.removeRef(key)

    fun decay(type: SemanticType): SemanticType = type.removeRef(key).decay(key)
}