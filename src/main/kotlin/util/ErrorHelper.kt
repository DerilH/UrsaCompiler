package org.derilh.util

import org.derilh.analyzer.DeclSymbol
import org.derilh.core.OpResult
import org.derilh.core.Options

class ErrorHelper {
    companion object {
        fun alreadyDefined(new: DeclSymbol, old: DeclSymbol, vararg args: Any): OpResult.Failure {
            return OpResult.failure("${getSymbolText(new)} has already been defined as a ${getSymbolText(old)}", new.astNode, args)
        }

        private fun getSymbolText(symbol: DeclSymbol): String = when (symbol) {
            is DeclSymbol.VariableDecl -> "variable ${symbol.name}"
            is DeclSymbol.ConstructorDecl -> "Constructor ${symbol.name}"
            is DeclSymbol.OperatorFunctionDecl -> "Operator ${symbol.name}"
            is DeclSymbol.FunctionDecl -> "function ${symbol.name}"
            is DeclSymbol.ClassDecl -> "class ${symbol.name}"
            is DeclSymbol.NamespaceDecl -> "namespace ${symbol.name}"
            is DeclSymbol.FunctionOverloadSet -> "<function_overload_set>"
        }
        fun getStackTrace(options: Options): Array<StackTraceElement>? {
            return if(options.traceErrors) {
                Throwable().stackTrace;
            } else null;
        }
    }
}