package org.derilh.ast

import org.derilh.ast.api.IExpressionNode
import org.derilh.ast.api.IIdentifierNode
import org.derilh.core.ClassType
import org.derilh.core.ICVQualifier
import org.derilh.core.INoExceptSpecifier
import org.derilh.core.IRefQualifier
import org.derilh.core.SourceLocation

sealed interface StorageClassSpecifier : DeclSpecifier {
    data class ThreadLocal(override val location: SourceLocation) : StorageClassSpecifier
    data class Static(override val location: SourceLocation) : StorageClassSpecifier
    data class Extern(override val location: SourceLocation) : StorageClassSpecifier
    data class Mutable(override val location: SourceLocation) : StorageClassSpecifier
}

sealed class FunctionSpecifier : DeclSpecifier {
    data class Virtual(override val location: SourceLocation) : FunctionSpecifier()
    data class Explicit(val node: IExpressionNode?, override val location: SourceLocation) : FunctionSpecifier()
}

sealed interface DeclSpecifier {
    val location: SourceLocation;

    data class Friend(override val location: SourceLocation) : DeclSpecifier
    data class Typedef(override val location: SourceLocation) : DeclSpecifier
    data class Inline(override val location: SourceLocation) : DeclSpecifier
}

interface ConstExprSpecifier : DeclSpecifier {
    data class Constexpr(override val location: SourceLocation) : ConstExprSpecifier
    data class Consteval(override val location: SourceLocation) : ConstExprSpecifier
    data class Constinit(override val location: SourceLocation) : ConstExprSpecifier
}

data class DeclSpecifierSeq(
    val funcSpec: FunctionSpecifier? = null,
    val storageClassSpec: List<StorageClassSpecifier>? = null,
    val typeSpecs: List<TypeSpecifier>? = null,
    val friendSpec: DeclSpecifier? = null,
    val typedefSpec: DeclSpecifier? = null,
    val constexprSpec: ConstExprSpecifier? = null,
    val inlineSpec: DeclSpecifier? = null,
    val location: SourceLocation
) {
    val isFriend get() = friendSpec != null
    val isInline get() = inlineSpec != null
    val isTypedef get() = typedefSpec != null
    val hasDeclaredId get() = typeSpecs?.any { it is SimpleTypeSpecifier.Id } ?: false

    fun getElaborated(): ClassDeclarationNode? {
        return typeSpecs?.filterIsInstance<ClassDeclarationNode>()?.firstOrNull()
    }

    fun isDefining(): Boolean {
        return typeSpecs?.any { it is ClassDefinitionNode } ?: false
    }

    fun isOnlyTypeSpec(): Boolean {
        return funcSpec == null && storageClassSpec == null && !isFriend && !isTypedef && constexprSpec == null && !isInline
    }

    fun hasTypeSpec(): Boolean {
        return typeSpecs?.isNotEmpty() ?: false
    }
}

sealed interface TypeSpecifier : DeclSpecifier
sealed interface DefiningTypeSpecifier : TypeSpecifier

sealed interface SimpleTypeSpecifier : TypeSpecifier {
    data class Keyword(val keyword: org.derilh.core.Keyword, override val location: SourceLocation) : SimpleTypeSpecifier
    data class Placeholder(val isDeclTypeAuto: Boolean, override val location: SourceLocation) : SimpleTypeSpecifier
    data class Decltype(val expr: IExpressionNode, override val location: SourceLocation) : SimpleTypeSpecifier
    data class Id(val expr: IIdentifierNode, override val location: SourceLocation) : SimpleTypeSpecifier
}

sealed interface CVQualifier : TypeSpecifier {
    data class Const(override val location: SourceLocation) : CVQualifier, ICVQualifier.Const
    data class Volatile(override val location: SourceLocation) : CVQualifier, ICVQualifier.Volatile
    data class Restrict(override val location: SourceLocation) : CVQualifier, ICVQualifier.Restrict // EXPANSION REQUIRED
}

sealed interface RefQualifier {
    data class LValue(val location: SourceLocation) : RefQualifier, IRefQualifier.LValue
    data class RValue(val location: SourceLocation) : RefQualifier, IRefQualifier.RValue
}

data class TypenameSpecifier(val id: IdentifierNode, override val location: SourceLocation) : TypeSpecifier
data class NoexceptSpecifier(override val expr: ExpressionNode?, val location: SourceLocation) : INoExceptSpecifier
