package org.derilh.core

import org.derilh.ast.api.IExpressionNode

sealed interface ICVQualifier {
    interface Const : ICVQualifier
    interface Volatile : ICVQualifier
    interface Restrict : ICVQualifier //EXPANSION
}

sealed interface IRefQualifier {
    interface LValue : IRefQualifier
    interface RValue : IRefQualifier
}

interface INoExceptSpecifier {
    val expr: IExpressionNode?
}

data class FunctionQualifiers(
    val cvQualifiers: List<ICVQualifier> = emptyList(),
    val refQualifier: IRefQualifier? = null,
    val noExceptSpec: INoExceptSpecifier? = null
) {
    val isConst: Boolean get()  {return cvQualifiers.any{ it is ICVQualifier.Const };}
    val isVolatile: Boolean get() {return cvQualifiers.any{ it is ICVQualifier.Volatile};}
    val isNoExcept: Boolean get() {return noExceptSpec != null}
}