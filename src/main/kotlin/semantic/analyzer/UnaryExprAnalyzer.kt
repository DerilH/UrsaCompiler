package org.derilh.analyzer

import org.derilh.ast.ASTNode
import org.derilh.ast.ExpressionNode
import org.derilh.ast.PointerTypeNode
import org.derilh.ast.PrimitiveTypeNode
import org.derilh.ast.TypeNode
import org.derilh.ast.UnaryExpressionNode
import org.derilh.core.Operator
import org.derilh.core.PrimitiveTypeKind
import org.derilh.core.ValueCategory
import org.derilh.semantic.ExpressionInfo
import org.derilh.semantic.SemanticType
import org.derilh.semantic.isDeclared
import org.derilh.semantic.isLValueRef
import org.derilh.semantic.isPointer
import org.derilh.semantic.isPrimitive
import org.derilh.semantic.isRValueRef

class UnaryExprAnalyzer : NodeAnalyzer<UnaryExpressionNode> {
    override fun analyze(node: UnaryExpressionNode, ctx: AnalyzeContext): ASTNode {
        node.operand = ctx.findAnalyzer(node.operand).analyze(node.operand, ctx) as ExpressionNode
        val type = node.operand.resolvedType;
        val operandInfo = ExpressionInfo(type!!, node.operand.valueCategory!!, ctx.isNullPointerConstant(node.operand))
        if(type.isPointer()) {
            node.resolvedType = resolvePointerUnaryOpType(node.operand.resolvedType as SemanticType.Pointer, node, ctx);
            return node;
        }
        else {
            var isPrimitive = false;
            val scope = when {
                type.isPrimitive() || type.isRValueRef() && type.pointee.isPrimitive() ||type.isLValueRef() && type.pointee.isPrimitive() -> {isPrimitive = true; ctx.rootScope};
                type.isDeclared() -> {type.decl.scope}
                type.isRValueRef() && type.pointee.isDeclared() -> type.pointee.decl.scope;
                type.isLValueRef() && type.pointee.isDeclared() -> type.pointee.decl.scope;
                else -> {
                    ctx.error("Cannot apply operator ${node.operator} to type ${node.operand.resolvedType}")
                    return node;
                }
            }

            val params = if(node.operator == Operator.INCREMENT || node.operator == Operator.DECREMENT) {
                listOf(operandInfo, ExpressionInfo(ctx.types.int, ValueCategory.PRVALUE, false))
            } else listOf(operandInfo)
            val overloads = ctx.resolveOpOverloads(scope, node.operator,params)
            if(overloads.isEmpty()) {
                ctx.error("No matching operator overloads found for '${node.operator}'", node)
            }
            else if(overloads.size > 1) {
                ctx.error("Ambiguous operator overload ${node.operator}", node)
                //TODO: add ambiguous overloads print
            } else {
                val overload = overloads.first();
                node.functionDecl = overload.decl;
                node.resolvedType = overload.decl.returnType;
                //TODO: check if overload is valid for primitive (maybe not needed check for empty conversion sequence;

                if(!isPrimitive) {
                    node.operand = ctx.buildConversionNodes(node.operand,overload.sequences.first());
                }
            }

        }

        return node;
    }

    fun resolvePointerUnaryOpType(
        operand: SemanticType.Pointer,
        node: UnaryExpressionNode,
        ctx: AnalyzeContext
    ): SemanticType? {
        val pointee = operand.pointee

        return when (node.operator) {
            Operator.POINTER -> {
                if (pointee.isPrimitive(PrimitiveTypeKind.VOID)) {
                    ctx.error("Cannot dereference 'void*'", node)
                    null
                } else if (!pointee.isComplete) {
                    ctx.error("Cannot dereference pointer to incomplete type '${pointee}'", node)
                    null
                } else {
                    pointee
                }
            }

            Operator.AMP -> {
                ctx.types.getPointer(pointee = operand, isConst = false, isVolatile = false)
            }

            Operator.NOT -> {
                ctx.types.bool
            }

            Operator.PLUS -> {
                operand
            }

            Operator.INCREMENT, Operator.DECREMENT -> {
                if (operand.isConst) {
                    ctx.error("Cannot apply '${node.operator}' to const-qualified pointer", node)
                    null
                } else if (pointee.isPrimitive(PrimitiveTypeKind.VOID)) {
                    ctx.error("Arithmetic on a pointer to void is forbidden in standard C++", node)
                    null
                } else if (!pointee.isComplete) {
                    ctx.error("Arithmetic on a pointer to an incomplete type '${pointee}'", node)
                    null
                } else {
                    operand
                }
            }

            Operator.MINUS, Operator.BIT_NOT -> {
                ctx.error("Invalid argument type '${operand}' to unary expression '${node.operator}'", node)
                null
            }

            else -> null
        }
    }
}
