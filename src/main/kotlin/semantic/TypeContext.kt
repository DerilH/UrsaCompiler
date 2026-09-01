package org.derilh.semantic

import org.derilh.analyzer.DeclSymbol
import org.derilh.core.FunctionQualifiers
import org.derilh.core.PrimitiveTypeKind
import org.derilh.core.TypeInfo
import org.derilh.target.TargetInfo
import org.derilh.util.Util
import kotlin.concurrent.fixedRateTimer
import kotlin.math.max

class TypeContext(val target: TargetInfo) {
    private object PrivateToken

    class Key private constructor(
        private val context: TypeContext,
        @Suppress("UNUSED_PARAMETER") token: PrivateToken
    ) {
        companion object {
            internal fun create(context: TypeContext) = Key(context, PrivateToken)
        }
        fun getOrCreate(elementType: SemanticType): SemanticType = context.intern(elementType)

        /**
         * @return Type size info or null if the type is incomplete
         */
        fun calculateSizeInfo(type: SemanticType): TypeInfo? {
            return context.calculateSizeInfo(type)
        }
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

    val size_t = getPrimitive(target.types.sizeType)
    val ptrDiff_t = getPrimitive(target.types.ptrDiffType)

    val auto = intern(SemanticType.Auto(key = key))
    val voidPtr = getPointer(void)

    private val error = intern(SemanticType.Error)
    private val errorFunction = intern(getFunction(SemanticType.Error, emptyList(), FunctionQualifiers()))

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
    fun getBoundMethod(thisInfo: ExpressionInfo,classDecl: DeclSymbol.ClassDecl, function: SemanticType.Function, isConst: Boolean = false, isVolatile: Boolean = false): SemanticType.BoundMethod {
        return intern(SemanticType.BoundMethod(thisInfo, function, classDecl, key))
    }
    fun getAuto(isConst: Boolean, isVolatile: Boolean): SemanticType.Auto {
        return intern(SemanticType.Auto(isConst, isVolatile, key))
    }

    fun getOverloadSet(name: String, overloads: List<DeclSymbol.FunctionDecl>, isUnqualified: Boolean): SemanticType.OverloadSet {
        return SemanticType.OverloadSet(name, overloads, isUnqualified, key)
    }

    fun getBoundMethodSet(thisInfo: ExpressionInfo, name: String, overloads: List<DeclSymbol.FunctionDecl>): SemanticType.BoundMethodSet {
        return SemanticType.BoundMethodSet(thisInfo, name, overloads, key)
    }


    fun getError(): SemanticType = error
    fun getErrorFunction(): SemanticType.Function = errorFunction;

    fun dropCV(type: SemanticType): SemanticType = type.dropCV(key)
    fun addCV(type: SemanticType, isConst: Boolean, isVolatile: Boolean): SemanticType = type.addCV(isConst, isVolatile, key)
    fun removeRef(type: SemanticType): SemanticType = type.removeRef(key)

    fun decay(type: SemanticType): SemanticType = type.decay(key)


    /**
     * @return Type size info or null if the type is incomplete
     */
    fun calculateSizeInfo(type: SemanticType): TypeInfo? {
        if(!type.isComplete) return null;
        return when(type) {
            is SemanticType.Array -> {
                if(type.size == null) null;
                else {
                    val elSize = calculateSizeInfo(type.elementType) ?: return null;
                    TypeInfo(elSize.widthBits * type.size, elSize.alignBits)
                }
            }
            is SemanticType.Declared -> {
                type.decl.layout?.typeInfo;
            }
            is SemanticType.Reference, is SemanticType.RValueReference, is SemanticType.Pointer, is SemanticType.MemberPointer -> target.types.pointer;
            is SemanticType.Primitive -> target.getTypeInfo(type.kind)
            else -> null
        }
    };

    /**
     * Compile class layout for a given class declaration.
     * @return [StructLayout] or null if class or its any of it fields is incomplete
     */
    fun compileClassLayout(classDecl: DeclSymbol.ClassDecl): StructLayout? {
        if(!classDecl.hasDefinition) return null;
        val ordinary = classDecl.scope.ordinarySymbols;
        val fields = mutableListOf<FieldLayout>()
        val methods = mutableListOf<MethodLayout>()
        var offset = 0L
        //TODO: Add support for alignas
        var maxAlign = 8L;
        for(symbol in ordinary) {
            if(symbol is DeclSymbol.VariableDecl) {
                val typeInfo = calculateSizeInfo(symbol.type) ?: return null;
                maxAlign = max(maxAlign, typeInfo.alignBits)
                offset = Util.alignUp(offset, typeInfo.alignBits)
                fields += FieldLayout(symbol.name, symbol.type, typeInfo, offset);
                offset += typeInfo.widthBits;
            }
        }
        if(offset == 0L) {
            offset = 8L;
        }
        val structSize = Util.alignUp(offset, maxAlign)
        return StructLayout(fields, methods, TypeInfo(structSize, maxAlign),null);
    }
}