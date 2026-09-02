package org.derilh.util

import org.derilh.analyzer.DeclSymbol
import org.derilh.ast.ASTNode
import org.derilh.core.OpResult
import org.derilh.core.Options
import org.derilh.core.SourceLocation
import org.derilh.semantic.SemanticType
import org.derilh.semantic.analyzer.ViableCandidate

class ErrorHelper {
    companion object {
        fun cannotConvert(from: SemanticType, to: SemanticType, node: ASTNode): OpResult.Failure {
            return cannotConvert(from,to,node.location)
        }

        fun cannotConvert(from: SemanticType, to: SemanticType, location: SourceLocation?): OpResult.Failure {
            return if(location == null) {
                OpResult.failure("Cannot convert from ${from.toDisplayString()} to ${to.toDisplayString()}")
            } else OpResult.failure("Cannot convert from ${from.toDisplayString()} to ${to.toDisplayString()}", location)
        }

        fun ambiguousConversion(from: SemanticType, to: SemanticType, location: SourceLocation?): OpResult.Failure {
            return if(location == null) {
                OpResult.failure("Ambiguous conversion from ${from.toDisplayString()} to ${to.toDisplayString()}")
            } else OpResult.failure("Ambiguous conversion from ${from.toDisplayString()} to ${to.toDisplayString()}", location)
        }

        fun argsCountMiss(expectedCount: Int, passedCount: Int, location: SourceLocation?): OpResult.Failure {
            return if(location == null) {
                OpResult.failure("Function has ${expectedCount} arguments, but is called with ${passedCount} arguments")
            } else OpResult.failure("Function has ${expectedCount} arguments, but is called with ${passedCount} arguments", location)
        }

        fun alreadyDefined(new: DeclSymbol, old: DeclSymbol, vararg args: Any): OpResult.Failure {
            return OpResult.failure("${getSymbolText(new)} has already been defined as a ${getSymbolText(old)}", new.astNode, args)
        }

        fun <T> checkViableSet(viable: Collection<ViableCandidate<T>>, name: String, location: SourceLocation): OpResult<ViableCandidate<T>> {
            return when {
                viable.isEmpty() -> OpResult.failure("Cannot resolve symbol ${name}", location);
                viable.size == 1 -> OpResult.success(viable.first());
                else -> OpResult.failure("Ambiguous overloads for ${name}", location);
            }
        }

        private fun getSymbolText(symbol: DeclSymbol): String = when (symbol) {
            is DeclSymbol.VariableDecl -> "variable ${symbol.name}"
            is DeclSymbol.ConstructorDecl -> "Constructor ${symbol.name}"
            is DeclSymbol.OperatorFunctionDecl -> "Operator ${symbol.name}"
            is DeclSymbol.FunctionDecl -> "function ${symbol.name}"
            is DeclSymbol.ClassDecl -> "class ${symbol.name}"
            is DeclSymbol.NamespaceDecl -> "namespace ${symbol.name}"
            is DeclSymbol.FunctionOverloadSet -> "<function_overload_set>"
            is DeclSymbol.TypedefDecl -> "typedef ${symbol.name}"
        }
        fun getStackTrace(options: Options): Array<StackTraceElement>? {
            return if(options.traceErrors) {
                Throwable().stackTrace;
            } else null;
        }
    }
}