package org.derilh.lexer

import org.derilh.core.Keyword
import org.derilh.core.Operator
import org.derilh.core.Symbol
import org.derilh.lexer.KeywordToken.Companion.BY_NAME

sealed class Token(val location: SourceLocation) {
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

class EofToken(location: SourceLocation) : Token(location)

class IdToken(val value: String, location: SourceLocation) : Token(location) {
    override fun toString(): String {
        return "Id('$value')"
    }

    override infix fun isA(other: Any): Boolean {
        if(other !is String) return false;
        return value == other
    }
}

open class ValueToken<T>(val value: T, location: SourceLocation) : Token(location)
class BooleanToken(value: Boolean, location: SourceLocation) : ValueToken<Boolean>(value, location) {
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
class IntToken(value: Int, location: SourceLocation) : ValueToken<Int>(value, location) {
    override fun toString(): String {
        return "IntVal('$value')"
    }
}

class DoubleToken(value: Double, location: SourceLocation) : ValueToken<Double>(value, location) {
    override fun toString(): String {
        return "DoubleVal('$value')"
    }
}

class FloatToken(value: Float, location: SourceLocation) : ValueToken<Float>(value, location) {
    override fun toString(): String {
        return "FloatVal('$value')"
    }
}

class CharToken(value: Char, location: SourceLocation) : ValueToken<Char>(value, location) {
    override fun toString(): String {
        return "CharVal('$value')"
    }
}

class LiteralToken(value: String, location: SourceLocation) : ValueToken<String>(value, location) {
    override fun toString(): String {
        return "StringVal('$value')"
    }
}

class KeywordToken(val value: Keyword, location: SourceLocation) : Token(location) {
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

class SymbolToken(val value: Symbol, location: SourceLocation) : Token(location) {
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