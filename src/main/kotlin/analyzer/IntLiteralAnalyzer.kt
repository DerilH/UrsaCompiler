package org.derilh.analyzer

import org.derilh.ast.ASTNode
import org.derilh.ast.IntLiteralNode
import org.derilh.ast.PrimitiveTypeNode
import org.derilh.core.PrimitiveTypeKind
import org.derilh.core.Radix
import org.derilh.exceptions.SemanticException
import org.derilh.target.TargetInfo

private class IntLiteralAnalyzer : NodeAnalyzer<IntLiteralNode> {
    override fun analyze(node: IntLiteralNode, ctx: AnalyzeContext): ASTNode {
        val value = node.value
        val target = ctx.target

        if (node.isSizeT) {
            val targetIntType = if (node.isUnsigned) target.types.sizeType else target.types.ptrDiffType
            node.type = PrimitiveTypeNode(targetIntType)
            return node
        }

        val kind: PrimitiveTypeKind = if (node.isUnsigned) {
            when {
                node.isLongLong -> PrimitiveTypeKind.UNSIGNED_LONG_LONG

                node.isLong -> {
                    if (value <= target.types.maxULong) {
                        PrimitiveTypeKind.UNSIGNED_LONG
                    } else {
                        PrimitiveTypeKind.UNSIGNED_LONG_LONG
                    }
                }

                else -> when {
                    value <= target.types.maxUInt      -> PrimitiveTypeKind.UNSIGNED_INT
                    value <= target.types.maxULong     -> PrimitiveTypeKind.UNSIGNED_LONG
                    value <= target.types.maxULongLong -> PrimitiveTypeKind.UNSIGNED_LONG_LONG
                    else -> throw SemanticException(
                        "Unsigned integer literal '$value' is too large for target", node)
                }
            }
        } else {
            when {
                node.isLongLong -> when {
                    value <= target.types.maxLongLong -> PrimitiveTypeKind.LONG_LONG
                    node.radix != Radix.DECIMAL && value <= target.types.maxULongLong -> PrimitiveTypeKind.UNSIGNED_LONG_LONG
                    else -> throw SemanticException("Long long integer literal '$value' is out of range", node)
                }

                node.isLong -> when {
                    value <= target.types.maxLong      -> PrimitiveTypeKind.LONG
                    node.radix != Radix.DECIMAL && value <= target.types.maxULong -> PrimitiveTypeKind.UNSIGNED_LONG
                    value <= target.types.maxLongLong  -> PrimitiveTypeKind.LONG_LONG
                    node.radix != Radix.DECIMAL && value <= target.types.maxULongLong -> PrimitiveTypeKind.UNSIGNED_LONG_LONG
                    else -> throw SemanticException("Long integer literal '$value' is out of range", node)
                }

                else -> deduceUnsuffixedKind(node, target)
            }
        }

        node.type = PrimitiveTypeNode(kind)
        return node;
    }

    private fun deduceUnsuffixedKind(node: IntLiteralNode, target: TargetInfo): PrimitiveTypeKind {
        val v = node.value

        return if (node.radix == Radix.DECIMAL) {
            //int -> long -> long long -> unsigned long long(extension)
            when {
                v <= target.types.maxInt       -> PrimitiveTypeKind.INT
                v <= target.types.maxLong      -> PrimitiveTypeKind.LONG
                v <= target.types.maxLongLong  -> PrimitiveTypeKind.LONG_LONG
                v <= target.types.maxULongLong -> PrimitiveTypeKind.UNSIGNED_LONG_LONG // GCC/Clang extension
                else -> throw SemanticException("Decimal integer literal '$v' is too large for any integer type", node)
            }
        } else {
            // int -> unsigned int -> long -> unsigned long -> long long -> unsigned long long
            when {
                v <= target.types.maxInt       -> PrimitiveTypeKind.INT
                v <= target.types.maxUInt      -> PrimitiveTypeKind.UNSIGNED_INT
                v <= target.types.maxLong      -> PrimitiveTypeKind.LONG
                v <= target.types.maxULong     -> PrimitiveTypeKind.UNSIGNED_LONG
                v <= target.types.maxLongLong  -> PrimitiveTypeKind.LONG_LONG
                v <= target.types.maxULongLong -> PrimitiveTypeKind.UNSIGNED_LONG_LONG
                else -> throw SemanticException("Integer literal '$v' is too large for any integer type", node)
            }
        }
    }
}

