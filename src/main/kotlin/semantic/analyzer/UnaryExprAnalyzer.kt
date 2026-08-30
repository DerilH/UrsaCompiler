package org.derilh.analyzer

import org.derilh.ast.ASTNode
import org.derilh.ast.ExpressionNode
import org.derilh.ast.UnaryExpressionNode
import org.derilh.core.Operator
import org.derilh.core.PrimitiveTypeKind
import org.derilh.core.ValueCategory
import org.derilh.core.getOrElse
import org.derilh.semantic.ExpressionInfo
import org.derilh.semantic.SemanticType
import org.derilh.semantic.analyzer.SemanticAnalyzer
import org.derilh.semantic.isDeclared
import org.derilh.semantic.isLValueRef
import org.derilh.semantic.isPointer
import org.derilh.semantic.isPrimitive
import org.derilh.semantic.isRValueRef
import org.derilh.util.ErrorHelper

class UnaryExprAnalyzer : NodeAnalyzer<UnaryExpressionNode> {
    override fun analyze(node: UnaryExpressionNode, ctx: AnalyzeContext): ASTNode {
        node.operand = ctx.findAnalyzer(node.operand).analyze(node.operand, ctx) as ExpressionNode
        val type = node.operand.resolvedType;
        val vc = node.operand.valueCategory;
        if (type == null || vc == null) {
            ctx.error("Could not resolve type of operand", node.operand)
            return node;
        }

        val operandInfo = ExpressionInfo(type, vc, ctx.isNullPointerConstant(node.operand))

        if (type is SemanticType.Function && node.operator == Operator.AMP) {
            node.resolvedType = ctx.types.decay(type)
            node.valueCategory = ValueCategory.PRVALUE
        } else if (type.isPointer()) {
            val info = resolvePointerUnaryOpType(node.operand.resolvedType as SemanticType.Pointer, node, ctx);
            node.resolvedType = info?.type;
            node.valueCategory = info?.valueCategory;
            return node;
        } else {
            var isPrimitive = false;
            val scope = when {
                type.isPrimitive() || type.isRValueRef() && type.pointee.isPrimitive() || type.isLValueRef() && type.pointee.isPrimitive() -> {
                    isPrimitive = true; ctx.rootScope
                };
                type.isDeclared() -> {
                    ctx.scope
                }

                type.isRValueRef() && type.pointee.isDeclared() -> ctx.scope;
                type.isLValueRef() && type.pointee.isDeclared() -> ctx.scope;
                else -> {
                    ctx.error("Cannot apply operator ${node.operator} to type ${node.operand.resolvedType}", node)
                    return node;
                }
            }

            val additionalParam = if (node.operator == Operator.INCREMENT || node.operator == Operator.DECREMENT) {
                ExpressionInfo(ctx.types.int, ValueCategory.PRVALUE, false)
            } else null
            val overloads = ctx.resolveOpOverloads(scope, node.operator, false, operandInfo, additionalParam)
            val result = ErrorHelper.checkViableSet(overloads, "${SemanticAnalyzer.OPERATOR_FUN_PREFIX}${node.operator}", node.location!!).getOrElse { ctx.error(it); return node; }

            node.functionDecl = result.decl;
            node.resolvedType = result.decl.returnType;
            node.valueCategory = ctx.getRefValueCategory(result.decl.returnType)
            //TODO: check if overload is valid for primitive (maybe not needed check for empty conversion sequence;
            //TODO: Add replacing of binary expression to overload call if not builtin overload
            if (!isPrimitive) {
                node.operand = ctx.buildConversionSeq(node.operand, result.sequences.first());
            }
        }

        return node;
    }

    fun resolvePointerUnaryOpType(
        operand: SemanticType.Pointer,
        node: UnaryExpressionNode,
        ctx: AnalyzeContext
    ): ExpressionInfo? {
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
                    ExpressionInfo(pointee, ValueCategory.LVALUE, false)
                }
            }

            Operator.AMP -> {
                val t = ctx.types.getPointer(pointee = operand, isConst = false, isVolatile = false)
                ExpressionInfo(t, ValueCategory.PRVALUE, false)
            }

            Operator.NOT -> {
                ExpressionInfo(ctx.types.bool, ValueCategory.PRVALUE, false)
            }

            Operator.PLUS -> {
                ExpressionInfo(operand, ValueCategory.PRVALUE, false)
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
                    ExpressionInfo(ctx.types.bool, ValueCategory.LVALUE, false)
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
