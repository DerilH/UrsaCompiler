package org.derilh.analyzer

import org.derilh.ast.ASTNode
import org.derilh.ast.CharLiteralNode
import org.derilh.core.CharPrefix
import org.derilh.core.Constants
import org.derilh.core.PrimitiveTypeKind
import org.derilh.core.toPrimitiveKind
import org.derilh.exceptions.SemanticProblem
import org.derilh.util.Util

class CharLiteralAnalyzer : NodeAnalyzer<CharLiteralNode> {

    override fun analyze(node: CharLiteralNode, ctx: AnalyzeContext): ASTNode {
        var codePoints = node.value

        if (codePoints.isEmpty()) {
            ctx.error("Empty character constant", node)
            return node
        }

        node.isMultiChar = codePoints.size > 1

        if (node.isMultiChar) {
            if(node.prefix != CharPrefix.NONE) {
                ctx.error("Char typer prefixes cannot be used with multi-character literals", node)
                return node
            }
            ctx.warn("Multi-character character constant", node)
        }

        codePoints = if (node.isMultiChar && codePoints.size > 4) {
            ctx.warn("Multi-character character constant is too long, truncated to 4 symbols", node)
            intArrayOf(codePoints[0], codePoints[1], codePoints[2], codePoints[3])
        } else {
            codePoints
        }

        node.numericValue = calculateNumericValue(codePoints, node.prefix, ctx, node)

        val kind = deduceCharKind(node)

        node.resolvedType = ctx.types.getPrimitive(kind)
        node.evaluated = node.value
        return node
    }

    private fun calculateNumericValue(text: IntArray, prefix: CharPrefix, ctx: AnalyzeContext, node: ASTNode): ULong {
        val target = ctx.target
        var numericValue = 0UL

        val effectivePrefix = if (prefix == CharPrefix.WIDE) {
            when (target.types.wchar_t.widthBits) {
                8  -> CharPrefix.UTF8
                16 -> CharPrefix.UTF16
                32 -> CharPrefix.UTF32
                else -> throw IllegalStateException("wchar_t has invalid size in target info: ${target.types.wchar_t.widthBits}")
            }
        } else prefix

        if (effectivePrefix != CharPrefix.NONE && text.size > 1) {
            ctx.error("Multi-character literal not supported for prefixed literals ($prefix)")
            return 0UL;
        }

        val (bitWidth, maxCharVal) = when (effectivePrefix) {
            CharPrefix.NONE -> {
                val width = target.types.char.widthBits
                val maxVal = Util.maxValueForByTypeWidth(width, false)
                width to maxVal
            }
            CharPrefix.UTF8  -> 8 to Constants.UNSIGNED_BYTE_MAX.toULong()
            CharPrefix.UTF16 -> 16 to Constants.UNSIGNED_2BYTE_MAX.toULong()
            CharPrefix.UTF32 -> 32 to Constants.UNSIGNED_4BYTE_MAX.toULong()
            else -> throw IllegalStateException("Cannot handle prefix: $effectivePrefix")
        }

        var accumulatedBits = 0
        for (codePoint in text) {
            val uCodePoint = codePoint.toUInt().toULong()

            if (uCodePoint > maxCharVal) {
                ctx.error("Character value $codePoint is out of range for prefix $prefix (max: $maxCharVal)", node)
                return 0UL;
            }

            if (accumulatedBits + bitWidth > 64) {
                ctx.error("Character literal bit width exceeds 64-bit storage limit", null)
                return 0UL;
            }

            numericValue = (numericValue shl bitWidth) or uCodePoint
            accumulatedBits += bitWidth
        }

        return numericValue
    }

    private fun deduceCharKind(node: CharLiteralNode): PrimitiveTypeKind {
        if (node.isMultiChar) {
            return PrimitiveTypeKind.INT
        }

        return node.prefix.toPrimitiveKind()
    }
}