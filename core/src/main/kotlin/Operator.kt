package org.derilh.core

enum class Precedence {
    LOWEST,         // 0: Базовий/найнижчий стан
    ASSIGNMENT,     // 1: =, +=, -=, *=, /=, %=, <<=, >>=, &=, ^=, |=
    CONDITIONAL,    // 2: ?:
    LOGICAL_OR,     // 3: ||
    LOGICAL_AND,    // 4: &&
    BITWISE_OR,     // 5: |
    BITWISE_XOR,    // 6: ^
    BITWISE_AND,    // 7: & (бінарний)
    EQUALITY,       // 8: ==, !=
    RELATIONAL,     // 9: <, <=, >, >=, <=>
    BITWISE_SHIFT,  // 10: <<, >>
    ADDITIVE,       // 11: +, -
    MULTIPLICATIVE, // 12: *, /, %
    POINTER_MEMBER, // 13: .*, ->* (Доступ до членів за вказівником)
    UNARY,          // 14: !, ~, -x, +x, ++x, --x, *ptr, &x, ::
    POSTFIX;        // 15: x++, x--, ., ->, (), [] (найвищий)

    fun isRightAssociative(): Boolean {
        return this == ASSIGNMENT || this == CONDITIONAL
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
    ADD_ASSIGN("+=", Precedence.ASSIGNMENT, isBinary = true),
    MINUS_ASSIGN("-=", Precedence.ASSIGNMENT, isBinary = true),
    MULT_ASSIGN("*=", Precedence.ASSIGNMENT, isBinary = true),
    DIV_ASSIGN("/=", Precedence.ASSIGNMENT, isBinary = true),
    MOD_ASSIGN("%=", Precedence.ASSIGNMENT, isBinary = true),
    LSHIFT_ASSIGN("<<=", Precedence.ASSIGNMENT, isBinary = true),
    RSHIFT_ASSIGN(">>=", Precedence.ASSIGNMENT, isBinary = true),
    BIT_AND_ASSIGN("&=", Precedence.ASSIGNMENT, isBinary = true),
    BIT_XOR_ASSIGN("^=", Precedence.ASSIGNMENT, isBinary = true),
    BIT_OR_ASSIGN("|=", Precedence.ASSIGNMENT, isBinary = true),
    BIT_OR("|", Precedence.BITWISE_OR, isBinary = true),
    BIT_XOR("^", Precedence.BITWISE_XOR, isBinary = true),
    OR("||", Precedence.LOGICAL_OR, isBinary = true),
    AND("&&", Precedence.LOGICAL_AND, isBinary = true),
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
    ARROW("->", Precedence.POSTFIX, isBinary = true),
    TRIPLE_DOT("...", Precedence.POSTFIX),
    HASH("#", Precedence.POSTFIX),
    DOUBLE_HASH("##", Precedence.POSTFIX);


    fun isAssignment(): Boolean {
        return this.precedence == Precedence.ASSIGNMENT
    }
}