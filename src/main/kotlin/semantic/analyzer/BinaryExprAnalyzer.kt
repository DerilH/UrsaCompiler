package org.derilh.analyzer

import org.derilh.ast.ASTNode
import org.derilh.ast.BinaryExpressionNode
import org.derilh.ast.CallExpressionNode
import org.derilh.ast.ExpressionNode
import org.derilh.core.OpResult
import org.derilh.core.Operator
import org.derilh.core.PrimitiveTypeKind
import org.derilh.core.ValueCategory
import org.derilh.core.getOrElse
import org.derilh.semantic.ExpressionInfo
import org.derilh.semantic.SemanticType
import org.derilh.semantic.analyzer.CallExprAnalyzer
import org.derilh.semantic.isDeclared
import org.derilh.semantic.isPointer
import org.derilh.semantic.isPrimitive
import org.derilh.util.ErrorHelper

class BinaryExprAnalyzer : NodeAnalyzer<BinaryExpressionNode> {
    override fun analyze(node: BinaryExpressionNode, ctx: AnalyzeContext): ASTNode {
        node.left = ctx.analyze(node.left, ctx.scope) as ExpressionNode
        node.right = ctx.analyze(node.right, ctx.scope) as ExpressionNode

        val leftType = node.left.resolvedType;
        val rightType = node.right.resolvedType;

        if (leftType == null || rightType == null) {
            return node; }
        val canonLeft = leftType.canonical
        val canonRight = ctx.types.decay(rightType.canonical)

        if (!node.operator.isBinary) {
            ctx.error("Operator '${node.operator}' is not a binary operator", node)
            return node;
        }

        val leftVc = node.left.valueCategory!!;
        val rightVc = node.right.valueCategory!!;

        val leftInfo = ExpressionInfo(leftType, leftVc, ctx.isNullPointerConstant(node.left));
        val rightInfo = ExpressionInfo(rightType, rightVc, ctx.isNullPointerConstant(node.right));

        if (canonLeft is SemanticType.Array && node.operator.isAssignment()) {
            ctx.error("Cannot assign to an array type $leftType", node)
            return node;
        }

        val result = when {
            canonLeft.isPrimitive() && canonRight.isPrimitive() -> resolvePrimitivesOp(canonLeft as SemanticType.Primitive, canonRight as SemanticType.Primitive, node, ctx);
            (canonLeft.isPointer() || canonRight.isPointer()) && (canonRight.isPrimitive() || canonLeft.isPrimitive()) -> resolvePointerPrimitiveOp(canonLeft, canonRight, node, ctx);
            canonLeft.isPointer() && canonRight.isPointer() -> resolvePointersOp(canonLeft as SemanticType.Pointer, canonRight as SemanticType.Pointer, node, ctx);
            canonRight.isDeclared() || canonLeft.isDeclared() -> {
                val overloads = ctx.resolveOpOverloads(ctx.scope, node.operator, true, leftInfo, rightInfo)
                if (overloads.size > 1) OpResult.failure("Ambiguous operator overload for type ${leftType} and ${rightType}", node)
                else if (overloads.isEmpty()) OpResult.failure("No operator overload for type ${leftType} and ${rightType}", node)
                else {
                    val over = overloads.first();
                    node.left = ctx.buildConversionSeq(node.left, over.sequences[0])
                    node.right = ctx.buildConversionSeq(node.right, over.sequences[1])

                    node.functionDecl = over.decl;
                    node.resolvedType = over.decl.returnType;
                    node.valueCategory = ctx.getRefValueCategory(over.decl.returnType);
                    OpResult.success(node)
                }
            }

            else -> OpResult.failure("Cannot apply operator '${node.operator}' to types '${leftType}' and '${rightType}'", node)
        }
        return result.getOrElse { ctx.error(it); return node; }
    }


    private fun resolvePointerPrimitiveOp(leftType: SemanticType, rightType: SemanticType, node: BinaryExpressionNode, ctx: AnalyzeContext): OpResult<ExpressionNode> {
        val pointerType = if (leftType.isPointer()) leftType else rightType;
        val primitiveType = if (leftType.isPointer()) rightType else leftType;
        val primitiveNode = if (leftType.isPointer()) node.right else node.left;
        pointerType as SemanticType.Pointer
        primitiveType as SemanticType.Primitive

        var expectedLeftType: SemanticType = leftType;
        var expectedRightType: SemanticType = rightType;
        var resolvedType: SemanticType;
        var resolvedVC = ValueCategory.PRVALUE;
        when (node.operator) {
            Operator.PLUS -> {
                if (!primitiveType.kind.isInt) return OpResult.failure("Cannot apply operator '${node.operator}' to types '${leftType}' and '${rightType}'", node)
                if (!pointerType.pointee.isComplete) return OpResult.failure("Cannot apply operator '${node.operator}' to incomplete type pointer ${pointerType}'", node)

                if (leftType.isPrimitive()) {
                    expectedLeftType = ctx.types.getPrimitive(ctx.target.types.ptrDiffType);
                } else {
                    expectedRightType = ctx.types.getPrimitive(ctx.target.types.ptrDiffType);
                }

                resolvedType = pointerType
            }

            Operator.MINUS -> {
                if (!primitiveType.kind.isInt) return OpResult.failure("Cannot apply operator '${node.operator}' to types '${leftType}' and '${rightType}'", node)
                if (!pointerType.pointee.isComplete) return OpResult.failure("Cannot apply operator '${node.operator}' to incomplete type pointer ${pointerType}'", node)
                if (leftType.isPrimitive()) return OpResult.failure("Cannot apply operator '${node.operator}' to types '${leftType}' and '${rightType}'", node)
                expectedRightType = ctx.types.getPrimitive(ctx.target.types.ptrDiffType);
                resolvedType = pointerType
            }

            Operator.EQUAL, Operator.NOT_EQ -> {
                if (primitiveType.kind != PrimitiveTypeKind.NULLPTR && !ctx.isNullPointerConstant(primitiveNode)) return OpResult.failure("Cannot apply operator '${node.operator}' to types '${primitiveType}' and '${pointerType}'", node)
                resolvedType = ctx.types.bool;
            }

            Operator.AND, Operator.OR -> {
                expectedLeftType = ctx.types.bool;
                expectedRightType = ctx.types.bool;
                resolvedType = ctx.types.bool;
            }

            Operator.ADD_ASSIGN, Operator.MINUS_ASSIGN -> {
                if (!primitiveType.kind.isInt || !leftType.isPointer()) return OpResult.failure("Cannot apply operator '${node.operator}' to types '${leftType}' and '${rightType}'", node)
                if (node.left.valueCategory?.isRValue == true) return OpResult.failure("Cannot assign to rvalue", node)
                if (!pointerType.pointee.isComplete) return OpResult.failure("Cannot apply operator '${node.operator}' to incomplete type pointer ${pointerType}'", node)

                expectedRightType = ctx.types.getPrimitive(ctx.target.types.ptrDiffType);
                resolvedType = ctx.types.getReference(pointerType);
                resolvedVC = ValueCategory.LVALUE;
            }

            Operator.ASSIGN -> {
                if ((primitiveType.kind != PrimitiveTypeKind.NULLPTR && !ctx.isNullPointerConstant(primitiveNode)) || !leftType.isPointer()) return OpResult.failure("Cannot apply operator '${node.operator}' to types '${leftType}' and '${rightType}'", node)
                if (node.left.valueCategory?.isRValue == true) return OpResult.failure("Cannot assign to rvalue", node)
                expectedRightType = ctx.types.getPrimitive(ctx.target.types.ptrDiffType);
                resolvedType = ctx.types.getReference(pointerType);
                resolvedVC = ValueCategory.LVALUE;
            }

            else -> return OpResult.failure("Cannot apply operator '${node.operator}' to types '${leftType}' and '${rightType}'", node)
        }

        node.left = ctx.buildConversion(node.left, expectedLeftType, resolvedVC).getOrElse { return it; };
        node.right = ctx.buildConversion(node.right, expectedRightType, ValueCategory.PRVALUE).getOrElse { return it; };
        node.resolvedType = resolvedType;
        node.valueCategory = resolvedVC
        return OpResult.success(node)
    }

    private fun resolvePointersOp(leftType: SemanticType.Pointer, rightType: SemanticType.Pointer, node: BinaryExpressionNode, ctx: AnalyzeContext): OpResult<ExpressionNode> {
        var expectedLeftType: SemanticType = leftType;
        var expectedRightType: SemanticType = rightType;
        var resolvedType: SemanticType;
        var resolvedVC = ValueCategory.PRVALUE;
        when (node.operator) {
            Operator.MINUS -> {
                if (leftType.pointee !== rightType.pointee) return OpResult.failure("Cannot apply operator '${node.operator}' to types '${leftType}' and '${rightType}'", node)
                if (!leftType.pointee.isComplete) return OpResult.failure("Cannot apply operator '${node.operator}' to incomplete type pointer ${leftType}'", node)
                resolvedType = ctx.types.getPrimitive(ctx.target.types.ptrDiffType);
            }

            Operator.EQUAL, Operator.NOT_EQ, Operator.LESS, Operator.LESS_EQUAL, Operator.GREATER, Operator.GREATER_EQUAL -> {
                expectedLeftType = rightType
                resolvedType = ctx.types.bool;
            }

            Operator.AND, Operator.OR -> {
                expectedLeftType = ctx.types.bool;
                expectedRightType = ctx.types.bool;
                resolvedType = ctx.types.bool;
            }

            Operator.ASSIGN -> {
                if (node.left.valueCategory?.isRValue == true) return OpResult.failure("Cannot assign to rvalue", node)
                expectedRightType = leftType;
                resolvedType = ctx.types.getReference(leftType);
                resolvedVC = ValueCategory.LVALUE;
            }

            else -> return OpResult.failure("Cannot apply operator '${node.operator}' to types '${leftType}' and '${rightType}'", node)
        }
        node.left = ctx.buildConversion(node.left, expectedLeftType, resolvedVC).getOrElse { return it; };
        node.right = ctx.buildConversion(node.right, expectedRightType, ValueCategory.PRVALUE).getOrElse { return it; };
        node.resolvedType = resolvedType;
        node.valueCategory = resolvedVC
        return OpResult.success(node)
    }

    private fun resolvePrimitivesOp(leftType: SemanticType.Primitive, rightType: SemanticType.Primitive, node: BinaryExpressionNode, ctx: AnalyzeContext): OpResult<ExpressionNode> {
        val leftKind = leftType.kind;
        val rightKind = rightType.kind;
        //TODO: SET PROPER expected value category

        return when (node.operator) {
            Operator.PLUS, Operator.MINUS, Operator.POINTER, Operator.DIVIDE,
            Operator.AMP, Operator.BIT_OR, Operator.BIT_XOR -> performUAC(leftType, rightType, node, ctx)

            Operator.MOD -> {
                if (!leftKind.isInt || !rightKind.isInt) {
                    OpResult.failure("Cannot apply operator ${node.operator} to ${leftType} and ${rightType}", node)
                } else performUAC(leftType, rightType, node, ctx);
            }

            Operator.EQUAL, Operator.NOT_EQ, Operator.LESS,
            Operator.LESS_EQUAL, Operator.GREATER, Operator.GREATER_EQUAL -> {
                val node = performUAC(leftType, rightType, node, ctx).getOrElse { return it }
                node.resolvedType = ctx.types.bool;
                OpResult.success(node);
            }

            Operator.LBITSHIFT, Operator.RBITSHIFT -> {
                if (!leftKind.isInt || !rightKind.isInt) OpResult.failure("Cannot apply operator '${node.operator}' to types '${leftType}' and '${rightType}'", node)
                else {
                    val leftKind = ctx.target.promoteIntegralType(leftKind);
                    val rightKind = ctx.target.promoteIntegralType(rightKind);
                    node.left = ctx.buildConversion(node.left, ctx.types.getPrimitive(leftKind), ValueCategory.PRVALUE).getOrElse { return it; }
                    node.right = ctx.buildConversion(node.right, ctx.types.getPrimitive(rightKind), ValueCategory.PRVALUE).getOrElse { return it; }
                    node.resolvedType = node.left.resolvedType;
                    OpResult.success(node)
                }
            }

            Operator.ASSIGN, Operator.ADD_ASSIGN, Operator.MINUS_ASSIGN, Operator.MULT_ASSIGN,
            Operator.DIV_ASSIGN, Operator.MOD_ASSIGN, Operator.LSHIFT_ASSIGN, Operator.RSHIFT_ASSIGN,
            Operator.BIT_AND_ASSIGN, Operator.BIT_XOR_ASSIGN, Operator.BIT_OR_ASSIGN -> {
                if (node.left.valueCategory != ValueCategory.LVALUE) return OpResult.failure("Cannot assign to rvalue", node)
                if (leftType.isConst) return OpResult.failure("Cannot assign to readonly type $leftType", node)

                node.right = ctx.buildConversion(node.right, leftType, ValueCategory.PRVALUE).getOrElse { return it; }
                node.resolvedType = node.left.resolvedType;
                node.valueCategory = ValueCategory.LVALUE;
                OpResult.success(node)
            }


            Operator.SPACESHIP -> OpResult.failure("Spaceship operator is not supported yet", node)
            Operator.NOT, Operator.BIT_NOT, Operator.NAMESPACE, Operator.INCREMENT, Operator.DECREMENT -> OpResult.failure("Operator '${node.operator}' cannot be used in binary expression", node)

            Operator.OR, Operator.AND -> {
                node.left = ctx.buildConversion(node.left, ctx.types.bool, ValueCategory.PRVALUE).getOrElse { return it; }
                node.right = ctx.buildConversion(node.right, ctx.types.bool, ValueCategory.PRVALUE).getOrElse { return it; }
                node.resolvedType = ctx.types.bool;
                node.valueCategory = ValueCategory.PRVALUE;
                OpResult.success(node)
            }

            Operator.DOT_STAR, Operator.ARROW_STAR, Operator.DOT, Operator.ARROW -> OpResult.failure("Operator '${node.operator}' cannot be used on primitive type", node)
        }
    }

    fun performUAC(
        rawLeftType: SemanticType.Primitive,
        rawRightType: SemanticType.Primitive,
        node: BinaryExpressionNode,
        ctx: AnalyzeContext
    ): OpResult<ExpressionNode> {
        val leftTypeNoCV = ctx.types.dropCV(rawLeftType) as SemanticType.Primitive
        val rightTypeNoCV = ctx.types.dropCV(rawRightType) as SemanticType.Primitive

        val leftKind = leftTypeNoCV.kind
        val rightKind = rightTypeNoCV.kind

        val resolvedType = if (leftKind.isFloat || rightKind.isFloat) {
            val isBothFloat = leftKind.isFloat && rightKind.isFloat
            val isRanksEqual = leftKind.floatRank == rightKind.floatRank

            if ((leftKind.isInt && rightKind.isFloat) || (isBothFloat && (rightKind.floatRank > leftKind.floatRank || (!isRanksEqual && rightKind.floatSubRank > leftKind.floatRank)))) {
                rightTypeNoCV
            } else if ((leftKind.isFloat && rightKind.isInt) || (isBothFloat && (leftKind.floatRank > rightKind.floatRank || (!isRanksEqual && leftKind.floatSubRank > rightKind.floatRank)))) {
                leftTypeNoCV
            } else {
                return ErrorHelper.cannotConvert(leftTypeNoCV, rightTypeNoCV, node)
            }
        } else if (leftKind.isInt && rightKind.isInt) {
            val leftPromotedType = ctx.types.getPrimitive(ctx.target.promoteIntegralType(leftKind))
            val rightPromotedType = ctx.types.getPrimitive(ctx.target.promoteIntegralType(rightKind))

            val leftPromotedKind = leftPromotedType.kind
            val rightPromotedKind = rightPromotedType.kind

            if (leftPromotedKind == rightPromotedKind) {
                leftPromotedType
            } else if (leftPromotedKind.isUnsigned == rightPromotedKind.isUnsigned) {
                if (leftPromotedKind.intRank > rightPromotedKind.intRank) {
                    leftPromotedType
                } else {
                    rightPromotedType
                }
            } else {
                val unsignedType = if (leftPromotedKind.isUnsigned) leftPromotedType else rightPromotedType
                val signedType = if (leftPromotedKind.isUnsigned) rightPromotedType else leftPromotedType

                if (unsignedType.kind.intRank >= signedType.kind.intRank) {
                    unsignedType
                } else if (ctx.target.canFitInType(unsignedType.kind, signedType.kind)) {
                    signedType
                } else {
                    ctx.types.getPrimitive(signedType.kind.toUnsigned())
                }
            }
        } else {
            return ErrorHelper.cannotConvert(leftTypeNoCV, rightTypeNoCV, node)
        }

        node.left = ctx.buildConversion(node.left, resolvedType, ValueCategory.PRVALUE).getOrElse { return it }
        node.right = ctx.buildConversion(node.right, resolvedType, ValueCategory.PRVALUE).getOrElse { return it }
        node.resolvedType = resolvedType
        node.valueCategory = ValueCategory.PRVALUE

        return OpResult.success(node)
    }
}
