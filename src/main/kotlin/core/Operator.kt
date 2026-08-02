package org.derilh.core

enum class Precedence {
    LOWEST,         // 0: Базовий/найнижчий стан
    ASSIGNMENT,     // 1: =, +=, -=, *=, /=, %=, <<=, >>=, &=, ^=, |=
    LOGICAL_OR,     // 2: ||
    LOGICAL_AND,    // 3: &&
    BITWISE_OR,     // 4: |
    BITWISE_XOR,    // 5: ^
    BITWISE_AND,    // 6: & (бінарний)
    EQUALITY,       // 7: ==, !=
    RELATIONAL,     // 8: <, <=, >, >=, <=>
    BITWISE_SHIFT,  // 9: <<, >>
    ADDITIVE,       // 10: +, -
    MULTIPLICATIVE, // 11: *, /, %
    POINTER_MEMBER, // 12: .*, ->* (Доступ до членів за вказівником)
    UNARY,          // 13: !, ~, -x, +x, ++x, --x, *ptr, &x, ::
    POSTFIX;         // 14: x++, x--, ., ->, (), [] (найвищий)

    fun isRightAssociative(): Boolean {
        return this == ASSIGNMENT
    }

    fun decrement(): Precedence {
        val entries = Precedence.entries
        val prevOrdinal = (this.ordinal - 1).coerceAtLeast(0)
        return entries[prevOrdinal]
    }
}

enum class Operator(
    val value: String,
    val precedence: Precedence,
    val isBinary: Boolean = false,
    val isUnary: Boolean = false,
    val isOverloadable: Boolean = true
) {
    ASSIGN("=", Precedence.ASSIGNMENT, isBinary = true),
    ADD_EQ("+=", Precedence.ASSIGNMENT, isBinary = true),
    MINUS_EQ("-=", Precedence.ASSIGNMENT, isBinary = true),
    MULT_EQ("*=", Precedence.ASSIGNMENT, isBinary = true),
    DIV_EQ("/=", Precedence.ASSIGNMENT, isBinary = true),
    MOD_EQ("%=", Precedence.ASSIGNMENT, isBinary = true),
    LSHIFT_EQ("<<=", Precedence.ASSIGNMENT, isBinary = true),
    RSHIFT_EQ(">>=", Precedence.ASSIGNMENT, isBinary = true),
    BIT_AND_EQ("&=", Precedence.ASSIGNMENT, isBinary = true),
    BIT_XOR_EQ("^=", Precedence.ASSIGNMENT, isBinary = true),
    BIT_OR_EQ("|=", Precedence.ASSIGNMENT, isBinary = true),

    OR("||", Precedence.LOGICAL_OR, isBinary = true),
    AND("&&", Precedence.LOGICAL_AND, isBinary = true),

    BIT_OR("|", Precedence.BITWISE_OR, isBinary = true),
    BIT_XOR("^", Precedence.BITWISE_XOR, isBinary = true),

    EQUAL("==", Precedence.EQUALITY, isBinary = true),
    NOT_EQ("!=", Precedence.EQUALITY, isBinary = true),
    LESS("<", Precedence.RELATIONAL, isBinary = true),
    LESS_EQUAL("<=", Precedence.RELATIONAL, isBinary = true),
    GREATER(">", Precedence.RELATIONAL, isBinary = true),
    GREATER_EQUAL(">=", Precedence.RELATIONAL, isBinary = true),
    SPACESHIP("<=>", Precedence.RELATIONAL, isBinary = true),

    LBITSHIFT("<<", Precedence.BITWISE_SHIFT, isBinary = true),
    RBITSHIFT(">>", Precedence.BITWISE_SHIFT, isBinary = true),

    PLUS("+", Precedence.ADDITIVE, isBinary = true, isUnary = true),
    MINUS("-", Precedence.ADDITIVE, isBinary = true, isUnary = true),

    POINTER("*", Precedence.MULTIPLICATIVE, isBinary = true, isUnary = true),
    DIVIDE("/", Precedence.MULTIPLICATIVE, isBinary = true),
    MOD("%", Precedence.MULTIPLICATIVE, isBinary = true),
    AMP("&", Precedence.BITWISE_AND, isBinary = true, isUnary = true),

    DOT_STAR(".*", Precedence.POINTER_MEMBER, isBinary = true, isOverloadable = false),
    ARROW_STAR("->*", Precedence.POINTER_MEMBER, isBinary = true),

    NOT("!", Precedence.UNARY, isUnary = true),
    BIT_NOT("~", Precedence.UNARY, isUnary = true),
    NAMESPACE("::", Precedence.UNARY, isOverloadable = false),

    INCREMENT("++", Precedence.POSTFIX, isUnary = true),
    DECREMENT("--", Precedence.POSTFIX, isUnary = true),
    DOT(".", Precedence.POSTFIX, isBinary = true, isOverloadable = false),
    ARROW("->", Precedence.POSTFIX, isBinary = true)
}