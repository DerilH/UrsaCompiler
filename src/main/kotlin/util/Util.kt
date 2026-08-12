package org.derilh.util
import kotlin.math.pow

class Util {
    companion object {
        fun codePointsToUtf16Filtered(codePoints: IntArray): String {
            return buildString {
                for (cp in codePoints) {
                    if (Character.isValidCodePoint(cp)) {
                        when (cp) {
                            '\n'.code -> append("\\n")
                            '\r'.code -> append("\\r")
                            '\t'.code -> append("\\t")
                            '\b'.code -> append("\\b")
                            '\\'.code -> append("\\\\")
                            else -> {
                                // Для остальных непечатных/управляющих символов используем Unicode-формат \uXXXX
                                if (Character.isISOControl(cp)) {
                                    append(String.format("\\u%04X", cp))
                                } else {
                                    appendCodePoint(cp)
                                }
                            }
                        }
                    } else {
                        append('?')
                    }
                }
            }
        }

        fun maxValueForByTypeWidth(bitWidth: Int, signed: Boolean): ULong {
            require(bitWidth in 1..64) { "Bit width must be between 1 and 64." }

            val targetBits = if (signed) bitWidth - 1 else bitWidth
            return if (targetBits == 64) ULong.MAX_VALUE else (1UL shl targetBits) - 1UL
        }
    }
}