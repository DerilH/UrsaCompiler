package semantic

import org.derilh.ast.CVQualifier
import org.derilh.ast.ClassDefinitionNode
import org.derilh.ast.DeclSpecifier
import org.derilh.ast.DeclSpecifierSeq
import org.derilh.ast.ExpressionNode
import org.derilh.ast.IdentifierNode
import org.derilh.ast.NoexceptSpecifier
import org.derilh.ast.ParameterNode
import org.derilh.ast.SimpleTypeSpecifier
import org.derilh.ast.StorageClassSpecifier
import org.derilh.ast.TypeSpecifier
import org.derilh.ast.TypenameSpecifier
import org.derilh.ast.FunctionSpecifier
import org.derilh.ast.ConstExprSpecifier
import org.derilh.core.FunctionQualifiers
import org.derilh.core.ICVQualifier
import org.derilh.core.IRefQualifier
import org.derilh.core.SourceLocation

sealed interface DeclaratorChunk {
    val location: SourceLocation
    data class Ptr(val kind: Kind, val cvQualifiers: List<CVQualifier>, override val location: SourceLocation) : DeclaratorChunk {
        enum class Kind { Pointer, RRef, LRef }
        val isConst: Boolean get() = cvQualifiers.any{it is CVQualifier.Const}
        val isVolatile: Boolean get() = cvQualifiers.any{it is CVQualifier.Volatile}
        val isRestrict: Boolean get() = cvQualifiers.any{it is CVQualifier.Restrict}
    }

    data class MemberPointer(val classId: IdentifierNode, val cvQualifiers: List<CVQualifier>, override val location: SourceLocation) : DeclaratorChunk {
        val isConst: Boolean get() = cvQualifiers.any{it is CVQualifier.Const}
        val isVolatile: Boolean get() = cvQualifiers.any{it is CVQualifier.Volatile}
    }

    data class Array(
        val sizeExpr: ExpressionNode?,
//        val attributes: List<Attribute> = emptyList()
        override val location: SourceLocation,
    ) : DeclaratorChunk

    data class Function(
        val params: List<ParameterNode>,
        val functionQualifiers: FunctionQualifiers,
        val trailingReturnType: TypeId?,
        override val location: SourceLocation
    ) : DeclaratorChunk
    {
        val defaultParamCount = params.count { it.declarator?.init != null }
    }

    fun toDisplayString(): String = when (this) {
        is Ptr -> {
            val operator = when (kind) {
                Ptr.Kind.Pointer -> "*"
                Ptr.Kind.LRef -> "&"
                Ptr.Kind.RRef -> "&&"
            }
            "$operator${formatQualifiers(cvQualifiers)}"
        }
        is MemberPointer -> "${classId.toDisplayString()}::*${formatQualifiers(cvQualifiers)}"
        is Array -> "[${sizeExpr?.toString() ?: ""}]"
        is Function -> buildString {
            append("(")
            append(params.joinToString(", ", transform = ParameterNode::toDisplayString))
            append(")")
            append(formatFunctionQualifiers(functionQualifiers))
            trailingReturnType?.let {
                append(" -> ")
                append(it.toDisplayString())
            }
        }
    }
}

data class Declarator(
    val id: IdentifierNode?,
    val init: ExpressionNode?,
    val chunks: List<DeclaratorChunk> = emptyList(),
    val location: SourceLocation
) {
    fun isAbstract(): Boolean {
        return id == null
    }

    fun getFunctionDeclarator(): DeclaratorChunk.Function? {
        return chunks.lastOrNull() as? DeclaratorChunk.Function
    }

    fun isFunctionDeclarator(): Boolean {
        return chunks.lastOrNull() is DeclaratorChunk.Function
    }

    fun toDisplayString(): String {
        var result = id?.toDisplayString() ?: ""
        var hasPrefix = false

        for (chunk in chunks) {
            when (chunk) {
                is DeclaratorChunk.Ptr, is DeclaratorChunk.MemberPointer -> {
                    result = chunk.toDisplayString() + result
                    hasPrefix = true
                }
                is DeclaratorChunk.Array -> {
                    if (hasPrefix) result = "($result)"
                    result += chunk.toDisplayString()
                    hasPrefix = false
                }
                is DeclaratorChunk.Function -> {
                    if (hasPrefix) result = "($result)"
                    result += chunk.toDisplayString()
                    hasPrefix = false
                }
            }
        }

        return result + (init?.let { " = $it" } ?: "")
    }
}

class TypeId(val declSpecifier: DeclSpecifierSeq, val declarator: Declarator?) {
    fun toDisplayString(): String =
        listOfNotNull(declSpecifier.toDisplayString(), declarator?.toDisplayString())
            .joinToString(" ")
}

private fun ParameterNode.toDisplayString(): String = buildString {
    if (isExplicitObjectParameter) append("this ")
    append(declSpec.toDisplayString())
    declarator?.toDisplayString()?.takeIf { it.isNotEmpty() }?.let {
        if (isNotEmpty()) append(" ")
        append(it)
    }
    init?.let {
        append(" = ")
        append(it)
    }
}

private fun DeclSpecifierSeq.toDisplayString(): String = buildList {
    funcSpec?.let { add(it.toDisplayString()) }
    storageClassSpec.orEmpty().forEach { add(it.toDisplayString()) }
    friendSpec?.let { add(it.toDisplayString()) }
    typedefSpec?.let { add(it.toDisplayString()) }
    constexprSpec?.let { add(it.toDisplayString()) }
    inlineSpec?.let { add(it.toDisplayString()) }
    typeSpecs.orEmpty().forEach { add(it.toDisplayString()) }
}.joinToString(" ")

private fun DeclSpecifier.toDisplayString(): String = when (this) {
    is StorageClassSpecifier.ThreadLocal -> "thread_local"
    is StorageClassSpecifier.Static -> "static"
    is StorageClassSpecifier.Extern -> "extern"
    is StorageClassSpecifier.Mutable -> "mutable"
    is FunctionSpecifier.Virtual -> "virtual"
    is FunctionSpecifier.Explicit -> "explicit"
    is DeclSpecifier.Friend -> "friend"
    is DeclSpecifier.Typedef -> "typedef"
    is DeclSpecifier.Inline -> "inline"
    is ConstExprSpecifier.Constexpr -> "constexpr"
    is ConstExprSpecifier.Consteval -> "consteval"
    is ConstExprSpecifier.Constinit -> "constinit"
    is CVQualifier.Const -> "const"
    is CVQualifier.Volatile -> "volatile"
    is CVQualifier.Restrict -> "restrict"
    is SimpleTypeSpecifier.Keyword -> keyword.value
    is SimpleTypeSpecifier.Placeholder -> if (isDeclTypeAuto) "decltype(auto)" else "auto"
    is SimpleTypeSpecifier.Decltype -> "decltype(${expr})"
    is SimpleTypeSpecifier.Id -> expr.toString()
    is TypenameSpecifier -> "typename ${id.toDisplayString()}"
    is TypeSpecifier -> toString()
    is ConstExprSpecifier -> "";
    is ClassDefinitionNode ->"";
}

private fun formatQualifiers(qualifiers: List<CVQualifier>): String =
    qualifiers.joinToString(separator = "", prefix = if (qualifiers.isEmpty()) "" else " ") {
        when (it) {
            is CVQualifier.Const -> "const "
            is CVQualifier.Volatile -> "volatile "
            is CVQualifier.Restrict -> "restrict "
        }
    }.trimEnd()

private fun formatFunctionQualifiers(qualifiers: FunctionQualifiers): String = buildString {
    qualifiers.cvQualifiers.joinToString(" ") {
        when (it) {
            is ICVQualifier.Const -> "const"
            is ICVQualifier.Volatile -> "volatile"
            is ICVQualifier.Restrict -> "restrict"
        }
    }.takeIf { it.isNotEmpty() }?.let { append(" ").append(it) }
    when (qualifiers.refQualifier) {
        is IRefQualifier.LValue -> append(" &")
        is IRefQualifier.RValue -> append(" &&")
        null -> {}
    }
    qualifiers.noExceptSpec?.let {
        append(" noexcept")
        (it as? NoexceptSpecifier)?.expr?.let { expr -> append("($expr)") }
    }
}