package org.derilh.analyzer

import org.derilh.ast.ASTNode
import org.derilh.ast.AbstractDeclaratorNode
import org.derilh.ast.ArrayAccessNode
import org.derilh.ast.ExpressionNode
import org.derilh.core.ValueCategory
import org.derilh.core.getOrElse
import org.derilh.core.ifFailure
import org.derilh.semantic.SemanticType
import org.derilh.semantic.isArray
import org.derilh.semantic.isPointer
import org.derilh.semantic.isPrimitive

class ArrayAccessAnalyzer : NodeAnalyzer<ArrayAccessNode> {
    override fun analyze(node: ArrayAccessNode, ctx: AnalyzeContext): ASTNode {
        var arrayExpr = ctx.analyze(node.operand, ctx.scope) as ExpressionNode
        var indexExpr = ctx.analyze(node.index, ctx.scope) as ExpressionNode

        var arrayType = arrayExpr.resolvedType ?: return node;
        var indexType = indexExpr.resolvedType ?: return node;

        // 2. Обработка C++ симметрии: если написали 0[ptr], меняем операнды местами
        if (!isPointerOrArray(arrayType) && isPointerOrArray(indexType)) {
            val tempExpr = arrayExpr
            arrayExpr = indexExpr
            indexExpr = tempExpr

            val tempType = arrayType
            arrayType = indexType
            indexType = tempType
        }

        if (!isPointerOrArray(arrayType)) {
            ctx.error("Subscripted value is not an array or pointer (got '${arrayType}')", arrayExpr)
            return node
        }

        if (!indexType.isPrimitive() || !indexType.kind.isInt) {
            ctx.error("Array subscript index is not an integer (got '${indexType}')", indexExpr)
            return node
        }

        if (arrayType.isArray()) {
            arrayType = ctx.types.decay(arrayType) as SemanticType.Pointer
            arrayExpr = ctx.buildConversion(arrayExpr, arrayType, ValueCategory.PRVALUE).getOrElse { ctx.error(it); return node }
        } else {
            arrayExpr = ctx.buildConversion(arrayExpr, arrayType, ValueCategory.PRVALUE).getOrElse { ctx.error(it); return node }
        }

        val sizeType = ctx.target.types.sizeType
        indexExpr = ctx.buildConversion(indexExpr, ctx.types.getPrimitive(sizeType), ValueCategory.PRVALUE).getOrElse { ctx.error(it); return node }

        val elementType = when (arrayType) {
            is SemanticType.Pointer -> arrayType.pointee
            else -> throw IllegalStateException("Should not be reached")
        }

        node.operand = arrayExpr
        node.index = indexExpr
        node.resolvedType = elementType
        node.valueCategory = ValueCategory.LVALUE

        return node
    }

    private fun isPointerOrArray(type: SemanticType): Boolean {
        return type.isPointer() || type.isArray();
    }
}