package org.derilh.analyzer

import org.derilh.ast.ASTNode
import org.derilh.ast.ArrayTypeNode
import org.derilh.ast.IntLiteralNode
import org.derilh.ast.PrimitiveTypeNode
import org.derilh.ast.StringConcatExpressionNode
import org.derilh.ast.StringLiteralNode
import org.derilh.core.CharPrefix
import org.derilh.core.Constants
import org.derilh.core.toPrimitiveKind
import org.derilh.exceptions.SemanticException
import org.derilh.semantic.SemanticType
import org.derilh.target.TargetInfo
import org.derilh.util.Util

class StringLiteralAnalyzer : NodeAnalyzer<StringLiteralNode> {
    override fun analyze(node: StringLiteralNode, ctx: AnalyzeContext): ASTNode {
        val prefix = node.prefix

        val realPrefix = if (prefix == CharPrefix.WIDE) {
            when (ctx.target.types.wchar_t.widthBits) {
                8 -> CharPrefix.UTF8
                16 -> CharPrefix.UTF16
                32 -> CharPrefix.UTF32
                else -> throw IllegalStateException("wchar_t has invalid size in target info: ${ctx.target.types.wchar_t.widthBits}")
            }
        } else {
            prefix
        }

        val maxCharVal = when (realPrefix) {
            CharPrefix.NONE -> Util.maxValueForByTypeWidth(ctx.target.types.char.widthBits, false)
            CharPrefix.UTF8 -> Constants.UNSIGNED_BYTE_MAX.toULong()
            CharPrefix.UTF16 -> Constants.UNSIGNED_2BYTE_MAX.toULong()
            CharPrefix.UTF32 -> Constants.UNSIGNED_4BYTE_MAX.toULong()
            else -> throw IllegalStateException("Cannot handle prefix: $realPrefix")
        }

        if(node.value.last() != 0) throw RuntimeException("String literal must end with a null character")

        for (codePoint in node.value) {

            val uCodePoint = codePoint.toUInt().toULong()

            if (uCodePoint > maxCharVal) {
                throw SemanticException(
                    "Character value $codePoint is out of range for string prefix $prefix (max: $maxCharVal)",
                    node
                )
            }
        }

        node.resolvedType = determineStringType(prefix, node.value.size.toLong(), ctx)
        return node;
    }


}

class StringConcatAnalyzer : NodeAnalyzer<StringConcatExpressionNode> {

    override fun analyze(node: StringConcatExpressionNode, ctx: AnalyzeContext): ASTNode {
        var prefix = CharPrefix.NONE
        var expectedListSize = 0;
        for (lit in node.literals) {
            if (lit.prefix != CharPrefix.NONE) {
                if (prefix == CharPrefix.NONE) {
                    prefix = lit.prefix
                } else if (prefix != lit.prefix) {
                    throw SemanticException("Cannot mix different prefixes in string concatenation", lit)
                }
            }
            expectedListSize += lit.value.size - 1
        }
        val concatArray = IntArray(expectedListSize + 1)
        var position = 0;

        for (lit in node.literals) {
            lit.prefix = prefix
            ctx.findAnalyzer(lit).analyze(lit, ctx)
            val copyCount = lit.value.size - 1
            lit.value.copyInto(concatArray, position, 0, copyCount)
            position += copyCount
        }

        concatArray[concatArray.size - 1] = 0
        val type = determineStringType(prefix, concatArray.size.toLong(), ctx)
        return StringLiteralNode(concatArray, prefix).also { it.resolvedType = type }
    }
}

private fun determineStringType(prefix: CharPrefix, length: Long, ctx: AnalyzeContext): SemanticType {
    return ctx.types.getArray(elementType = ctx.types.getPrimitive(kind = prefix.toPrimitiveKind(),isConst = true, isVolatile = false), size = length)
}