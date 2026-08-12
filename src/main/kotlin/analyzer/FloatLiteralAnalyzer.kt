package org.derilh.analyzer

import org.derilh.ast.ASTNode
import org.derilh.ast.FloatLiteralNode
import org.derilh.ast.PrimitiveTypeNode
import org.derilh.core.PrimitiveTypeKind
import org.derilh.exceptions.SemanticException
import java.math.BigDecimal

private class FloatLiteralAnalyzer : NodeAnalyzer<FloatLiteralNode> {
    override fun analyze(node: FloatLiteralNode, ctx: AnalyzeContext): ASTNode {
        val kind = when {
            node.isDouble && node.isLong -> PrimitiveTypeKind.LONG_DOUBLE
            node.isDouble -> PrimitiveTypeKind.DOUBLE
            !node.isDouble -> PrimitiveTypeKind.FLOAT
            else -> throw SemanticException("Invalid float provided",node)
        }

        val rawString = node.value
        validateFloatRange(rawString, kind, ctx, node)

        node.type = PrimitiveTypeNode(
            kind = kind,
            isConst = false,
            isVolatile = false
        )
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
            throw SemanticException("Invalid floating-point literal format '$rawText'", node)
        }
    }
}