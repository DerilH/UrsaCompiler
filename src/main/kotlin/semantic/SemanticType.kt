package org.derilh.semantic

import org.derilh.analyzer.DeclSymbol
import org.derilh.core.FunctionQualifiers
import org.derilh.core.PrimitiveTypeKind
import org.derilh.core.RefQualifier
import org.derilh.semantic.SemanticType.Declared
import org.derilh.semantic.SemanticType.Pointer
import org.derilh.semantic.SemanticType.Primitive
import org.derilh.semantic.SemanticType.RValueReference
import org.derilh.semantic.SemanticType.Reference
import java.util.Objects
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

sealed class SemanticType(val isConst: Boolean = false, val isVolatile: Boolean = false, val key: TypeContext.Key) {
    abstract val isComplete: Boolean
    abstract fun dropCV(key: TypeContext.Key): SemanticType;
    abstract fun addCV(isConst: Boolean, isVolatile: Boolean, key: TypeContext.Key): SemanticType;
    open fun removeRef(key: TypeContext.Key) = this
    open fun removePointer(key: TypeContext.Key) = this
    open fun decay(key: TypeContext.Key) = this

    override fun toString(): String {
        return toDisplayString()
    }
    class Primitive internal constructor(val kind: PrimitiveTypeKind, isConst: Boolean, isVolatile: Boolean, key: TypeContext.Key) : SemanticType(isConst, isVolatile, key) {
        override val isComplete: Boolean
            get() = kind != PrimitiveTypeKind.VOID

        override fun dropCV(key: TypeContext.Key): SemanticType {
            return key.getOrCreate(Primitive(kind, isConst = false, isVolatile = false, key = key).let { if (it === this) this else it })
        }

        override fun addCV(isConst: Boolean, isVolatile: Boolean, key: TypeContext.Key): SemanticType {
            return key.getOrCreate(Primitive(kind, isConst = isConst, isVolatile = isVolatile, key = key).let { if (it === this) this else it })
        }

        override fun toDisplayString(): String = "${qualifiersPrefix()}${kind.name.lowercase()}"
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Primitive) return false
            return kind == other.kind && isConst == other.isConst && isVolatile == other.isVolatile
        }

        override fun hashCode(): Int = Objects.hash(kind, isConst, isVolatile)
    }

    class Pointer internal constructor(val pointee: SemanticType, isConst: Boolean, isVolatile: Boolean, key: TypeContext.Key) : SemanticType(isConst, isVolatile, key) {
        override val isComplete: Boolean = true

        override fun dropCV(key: TypeContext.Key): SemanticType {
            return key.getOrCreate(Pointer(pointee, isConst = false, isVolatile = false, key = key).let { if (it === this) this else it })
        }

        override fun addCV(isConst: Boolean, isVolatile: Boolean, key: TypeContext.Key): SemanticType {
            return key.getOrCreate(Pointer(pointee, isConst = isConst, isVolatile = isVolatile, key = key).let { if (it === this) this else it })
        }

        override fun toDisplayString(): String = "${pointee.toDisplayString()}*${qualifiersPrefix().trimEnd()}"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Pointer) return false
            return pointee === other.pointee && isConst == other.isConst && isVolatile == other.isVolatile
        }

        override fun hashCode(): Int = Objects.hash(javaClass, pointee, isConst, isVolatile)
    }

    class Reference internal constructor(val pointee: SemanticType, key: TypeContext.Key) : SemanticType(key = key) {
        override val isComplete: Boolean get() = true

        override fun dropCV(key: TypeContext.Key): SemanticType = this
        override fun addCV(isConst: Boolean, isVolatile: Boolean, key: TypeContext.Key): SemanticType = this

        override fun removeRef(key: TypeContext.Key): SemanticType = pointee

        override fun toDisplayString(): String = "${pointee.toDisplayString()}&"
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Pointer) return false
            return pointee === other.pointee
        }

        override fun hashCode(): Int = Objects.hash(javaClass, pointee, isConst, isVolatile)
    }

    class RValueReference internal constructor(val pointee: SemanticType, key: TypeContext.Key) : SemanticType(key = key) {
        override val isComplete: Boolean get() = true

        override fun dropCV(key: TypeContext.Key): SemanticType = this
        override fun addCV(isConst: Boolean, isVolatile: Boolean, key: TypeContext.Key): SemanticType = this
        override fun removeRef(key: TypeContext.Key): SemanticType = pointee

        override fun toDisplayString(): String = "${pointee.toDisplayString()}&&"
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Pointer) return false
            return pointee === other.pointee
        }

        override fun hashCode(): Int = Objects.hash(javaClass, pointee, isConst, isVolatile)
    }

    class Array internal constructor(val elementType: SemanticType, val size: Long?, key: TypeContext.Key) : SemanticType(elementType.isConst, elementType.isVolatile, key) {
        override val isComplete: Boolean
            get() = size != null && elementType.isComplete


        override fun dropCV(key: TypeContext.Key): SemanticType {
            val newElementType = elementType.dropCV(key)
            if (newElementType === elementType) return this

            return key.getOrCreate(Array(key.getOrCreate(newElementType), size, key))
        }

        override fun addCV(isConst: Boolean, isVolatile: Boolean, key: TypeContext.Key): SemanticType {
            val newElementType = elementType.addCV(isConst, isVolatile, key)
            if (newElementType === elementType) return this

            return key.getOrCreate(Array(key.getOrCreate(newElementType), size, key))
        }

        override fun decay(key: TypeContext.Key): SemanticType {
            return key.getOrCreate(Pointer(elementType, isConst = false, isVolatile = false, key = key))
        }

        override fun toDisplayString(): String {
            val sizeStr = size?.toString() ?: ""
            return "${elementType.toDisplayString()}[$sizeStr]"
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Array) return false
            return elementType === other.elementType && size == other.size
        }

        override fun hashCode(): Int = Objects.hash(elementType, size)
    }

    class Function internal constructor(val returnType: SemanticType, val params: List<SemanticType>, val qualifiers: FunctionQualifiers, key: TypeContext.Key) : SemanticType(qualifiers.isConst, qualifiers.isVolatile, key) {
        override val isComplete: Boolean = true

        override fun dropCV(key: TypeContext.Key): SemanticType = this
        override fun addCV(isConst: Boolean, isVolatile: Boolean, key: TypeContext.Key): SemanticType = this
        override fun decay(key: TypeContext.Key): SemanticType {
            return key.getOrCreate(Pointer(this, isConst = false, isVolatile = false, key = key))
        }

        override fun toDisplayString(): String {
            val paramsStr = params.joinToString(", ") { it.toDisplayString() }
            val methodQuals = buildString {
                if (qualifiers.isConst) append(" const")
                if (qualifiers.isVolatile) append(" volatile")
                if (qualifiers.refQualifier == RefQualifier.LVALUE) append(" &")
                if (qualifiers.refQualifier == RefQualifier.RVALUE) append(" &&")
                if (qualifiers.isNoExcept) append(" noexcept")
            }
            return "${returnType.toDisplayString()}($paramsStr)$methodQuals"
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Function) return false
            if (returnType !== other.returnType || qualifiers != other.qualifiers) return false
            if (params.size != other.params.size) return false

            for (i in params.indices) {
                if (params[i] !== other.params[i]) return false
            }
            return true
        }

        override fun hashCode(): Int {
            var hash = Objects.hash(System.identityHashCode(returnType), qualifiers)
            for (param in params) {
                hash = 31 * hash + System.identityHashCode(param)
            }
            return hash
        }
    }


    class Declared internal constructor(val decl: DeclSymbol.ClassDecl, isConst: Boolean, isVolatile: Boolean, key: TypeContext.Key) : SemanticType(isConst, isVolatile, key) {
        override val isComplete: Boolean
            get() = decl.hasDefinition

        override fun toDisplayString(): String = "${qualifiersPrefix()}${decl.name}"

        override fun dropCV(key: TypeContext.Key): SemanticType {
            return Declared(decl, isConst = false, isVolatile = false, key = key).let { if (it === this) this else it }
        }

        override fun addCV(isConst: Boolean, isVolatile: Boolean, key: TypeContext.Key): SemanticType {
            return Declared(decl, isConst = isConst, isVolatile = isVolatile, key = key).let { if (it === this) this else it }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Declared) return false
            return decl === other.decl &&
                    isConst == other.isConst &&
                    isVolatile == other.isVolatile
        }

        override fun hashCode(): Int = Objects.hash(System.identityHashCode(decl), isConst, isVolatile)
    }

    class MemberPointer internal constructor(val decl: DeclSymbol.ClassDecl, val pointee: SemanticType, isConst: Boolean, isVolatile: Boolean, key: TypeContext.Key) : SemanticType(isConst, isVolatile, key) {
        override fun dropCV(key: TypeContext.Key): SemanticType {
            return key.getOrCreate(MemberPointer(decl, pointee, isConst = false, isVolatile = false, key = key).let { if (it === this) this else it })
        }

        override fun addCV(isConst: Boolean, isVolatile: Boolean, key: TypeContext.Key): SemanticType {
            return key.getOrCreate(MemberPointer(decl, pointee, isConst = isConst, isVolatile = isVolatile, key = key).let { if (it === this) this else it })
        }

        override val isComplete: Boolean = true
        override fun toDisplayString(): String {
            val className = decl.name
            val memberStr = pointee.toDisplayString()
            val qualSuffix = qualifiersPrefix()

            return if (pointee is Function) {
                val ret = pointee.returnType.toDisplayString()
                val params = pointee.params.joinToString(", ") { it.toDisplayString() }
                "$ret ($className::$qualSuffix)($params)"
            } else {
                "$memberStr $className::$qualSuffix".trimEnd()
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is MemberPointer) return false
            return decl === other.decl &&
                    pointee === other.pointee &&
                    isConst == other.isConst &&
                    isVolatile == other.isVolatile
        }

        override fun hashCode(): Int = Objects.hash(
            System.identityHashCode(decl),
            System.identityHashCode(pointee),
            isConst,
            isVolatile
        )
    }

    class Auto internal constructor(isConst: Boolean = false, isVolatile: Boolean = false, key: TypeContext.Key) : SemanticType(isConst, isVolatile, key) {

        override val isComplete: Boolean = false

        override fun dropCV(key: TypeContext.Key): SemanticType {
            return key.getOrCreate(Auto(key = key))
        }

        override fun addCV(isConst: Boolean, isVolatile: Boolean, key: TypeContext.Key): SemanticType {
            return key.getOrCreate(Auto(isConst, isVolatile, key))
        }

        override fun toDisplayString(): String {
            val prefix = qualifiersPrefix()
            return "$prefix auto"

        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Auto) return false
            return isConst == other.isConst &&
                    isVolatile == other.isVolatile
        }

        override fun hashCode(): Int =
            Objects.hash(isConst, isVolatile)
    }


    protected fun qualifiersPrefix(): String {
        val list = mutableListOf<String>()
        if (isConst) list.add("const")
        if (isVolatile) list.add("volatile")
        return if (list.isEmpty()) "" else list.joinToString(" ") + " "
    }

    abstract fun toDisplayString(): String

    fun containsAuto(): Boolean {
        return when (this) {
            is Primitive, is Declared -> false
            is Auto -> true
            is Pointer -> pointee.containsAuto()
            is Reference -> pointee.containsAuto()
            is RValueReference -> pointee.containsAuto()
            is Array -> elementType.containsAuto()

            is MemberPointer -> pointee.containsAuto()

            is Function -> {
                returnType.containsAuto() ||
                        params.any { it.containsAuto() }
            }
        }
    }

    val hasUndeducedAuto: Boolean get() = containsAuto()
}
@OptIn(ExperimentalContracts::class)
fun SemanticType.isFunctionPointer(): Boolean {
    contract {
        returns(true) implies (this@isFunctionPointer is SemanticType.Pointer)
    }
    return this is SemanticType.Pointer && this.pointee is SemanticType.Function
}

@OptIn(ExperimentalContracts::class)
fun SemanticType.isLValueRef(): Boolean {
    contract {
        returns(true) implies (this@isLValueRef is Reference)
    }
    return this is Reference
}

@OptIn(ExperimentalContracts::class)
fun SemanticType.isRValueRef(): Boolean {
    contract {
        returns(true) implies (this@isRValueRef is RValueReference)
    }
    return this is RValueReference
}

@OptIn(ExperimentalContracts::class)
fun SemanticType.isPointer(): Boolean {
    contract {
        returns(true) implies (this@isPointer is Pointer)
    }
    return this is Pointer
}

@OptIn(ExperimentalContracts::class)
fun SemanticType.isDeclared(): Boolean {
    contract {
        returns(true) implies (this@isDeclared is Declared)
    }
    return this is Declared
}

@OptIn(ExperimentalContracts::class)
fun SemanticType.isPrimitive(): Boolean {
    contract {
        returns(true) implies (this@isPrimitive is Primitive)
    }
    return this is Primitive
}

@OptIn(ExperimentalContracts::class)
fun SemanticType.isPrimitive(kind: PrimitiveTypeKind): Boolean {
    contract {
        returns(true) implies (this@isPrimitive is Primitive)
    }
    return this is Primitive && this.kind == kind;
}