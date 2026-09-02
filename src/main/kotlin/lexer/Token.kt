package org.derilh.lexer

import org.derilh.core.CharPrefix
import org.derilh.core.Keyword
import org.derilh.core.Operator
import org.derilh.core.Radix
import org.derilh.core.SourceLocation
import org.derilh.core.Symbol
import org.derilh.util.Util
import java.math.BigInteger

sealed class Token(val location: SourceLocation) {
    abstract val stringValue: String
    open infix fun isA(other: Any): Boolean = false
    open infix fun oneOf(other: List<Any>): Boolean {
        for (a in other) {
            return if (isA(a)) true
            else continue
        }
        return false;
    }

    open infix fun notA(other: Any): Boolean = !isA(other)

}

class EofToken(location: SourceLocation) : Token(location) {
    override val stringValue: String get() = ""
}

class IdToken(val value: String, location: SourceLocation) : Token(location) {
    override val stringValue: String get() = value

    override fun toString(): String {
        return "Id('$value')"
    }

    override infix fun isA(other: Any): Boolean {
        if(other !is String) return false;
        return value == other
    }
}

abstract class ValueToken<T>(val value: T, location: SourceLocation) : Token(location)
class BooleanToken(value: Boolean, location: SourceLocation) : ValueToken<Boolean>(value, location) {
    override val stringValue: String
        get() = value.toString()
    companion object {
        fun resolve(name: String, location: SourceLocation): BooleanToken? =
            when (name) {
                "true" -> BooleanToken(true, location)
                "false" -> BooleanToken(false, location)
                else -> null
            }
    }

    override fun toString(): String {
        return "BooleanVal('$value')"
    }
}

class IntToken(value: BigInteger, val radix: Radix, val isUnsigned: Boolean, val isLong: Boolean, val isLongLong: Boolean, val isSizeT: Boolean, location: SourceLocation) : ValueToken<BigInteger>(value, location) {
    override val stringValue: String get() = value.toString()

    override fun toString(): String {
        return "IntVal('$value')"
    }
}

class FloatToken(value: String, val isDouble: Boolean, val isLong: Boolean, location: SourceLocation) : ValueToken<String>(value, location) {
    override val stringValue: String get() = value
    override fun toString(): String {
        return "FloatVal('$value')"
    }
}

class CharToken(value: IntArray, val prefix: CharPrefix, location: SourceLocation) : ValueToken<IntArray>(value, location) {
    override val stringValue: String get() = Util.codePointsToUtf16Filtered(value)

    override fun toString(): String {
        return "CharVal('${Util.codePointsToUtf16Filtered(value)}')"
    }
}

class StringLiteralToken(value: IntArray, val prefix: CharPrefix, location: SourceLocation) : ValueToken<IntArray>(value, location) {
    override val stringValue: String get() = Util.codePointsToUtf16Filtered(value)

    override fun toString(): String {
        return "StringVal('${Util.codePointsToUtf16Filtered(value)}')"
    }
}

class KeywordToken(val value: Keyword, location: SourceLocation) : Token(location) {
    override val stringValue: String get() = value.value
    companion object {
        private val BY_NAME: Map<String, Keyword> =
            Keyword.entries.associateBy { it.value }

        fun resolve(name: String, location: SourceLocation) = BY_NAME[name]?.let { KeywordToken(it, location) }
    }

    override fun toString(): String {
        return "Keyword('$value')"
    }

    override infix fun isA(other: Any): Boolean {
        if(other !is Keyword) return false;
        return value == other
    }
}


class PreprocessorToken(val value: String, location: SourceLocation) : Token(location) {
    override val stringValue: String get() = value
}

class SymbolToken(val value: Symbol, location: SourceLocation) : Token(location) {
    override val stringValue: String
        get() = value.value.toString()
    companion object {
        private val BY_NAME: Map<Symbol, Symbol> =
            Symbol.entries.associateBy { it }

        fun resolve(name: Symbol, location: SourceLocation) = BY_NAME[name]?.let { SymbolToken(it, location) }
    }

    override fun toString(): String {
        return "Symbol('$value')"
    }

    override infix fun isA(other: Any): Boolean {
        if(other !is Symbol) return false;
        return value == other
    }
}

class OperatorToken(val value: Operator, location: SourceLocation) : Token(location) {
    override val stringValue: String get() = value.value

    val size: Int
        get() = value.value.length

    companion object {
        private val BY_NAME: Map<String, Operator> =
            Operator.entries.associateBy { it.value }

        fun resolve(value: String, location: SourceLocation): OperatorToken? {
            val fullOperator = BY_NAME[value] ?: BY_NAME[value[0].toString()]
            return fullOperator?.let { OperatorToken(it, location) }
        }
    }

    override fun toString(): String {
        return "Operator('$value')"
    }

    override infix fun isA(other: Any): Boolean {
        if (other !is Operator) return false
        return value == other
    }
}