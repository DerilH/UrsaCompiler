package org.derilh.analyzer

import org.derilh.ast.ASTNode
import org.derilh.ast.FloatLiteralNode
import org.derilh.core.PrimitiveTypeKind
import java.math.BigDecimal

class FloatLiteralAnalyzer : NodeAnalyzer<FloatLiteralNode> {
    override fun analyze(node: FloatLiteralNode, ctx: AnalyzeContext): ASTNode {
        val kind = when {
            node.isDouble && node.isLong -> PrimitiveTypeKind.LONG_DOUBLE
            node.isDouble -> PrimitiveTypeKind.DOUBLE
            !node.isDouble -> PrimitiveTypeKind.FLOAT
            else -> {
                ctx.error("Invalid float suffixes provided", node)
                return node
            }
        }

        val rawString = node.value
        validateFloatRange(rawString, kind, ctx, node)

        node.resolvedType = ctx.types.getPrimitive(kind)
        node.evaluated = node.value;
        return node
    }
    private fun validateFloatRange(
        rawText: String,
        kind: PrimitiveTypeKind,
        ctx: AnalyzeContext,
        node: FloatLiteralNode
    ) {
        val cleanText = rawText.lowercase()

        try {
            val value = BigDecimal(cleanText)

            when (kind) {
                PrimitiveTypeKind.FLOAT -> {
                    if (value.abs() > ctx.target.types.maxFloat) {
                        ctx.warn("Magnitude of floating-point constant is too large for type 'float'", node)
                    }
                }

                PrimitiveTypeKind.DOUBLE -> {
                    if (value.abs() > ctx.target.types.maxDouble) {
                        ctx.warn("Magnitude of floating-point constant is too large for type 'double'", node)
                    }
                }

                PrimitiveTypeKind.LONG_DOUBLE -> {
                    if (value.abs() > ctx.target.types.maxLongDouble) {
                        ctx.warn("Magnitude of floating-point constant is too large for type 'long double'", node)
                    }
                }

                else -> {}
            }
        } catch (e: NumberFormatException) {
            ctx.error("Invalid floating-point literal format '$rawText'", node)
        }
    }
}