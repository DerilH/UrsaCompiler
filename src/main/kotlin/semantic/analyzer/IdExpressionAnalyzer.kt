package org.derilh.semantic.analyzer

import org.derilh.analyzer.AnalyzeContext
import org.derilh.analyzer.DeclSymbol
import org.derilh.analyzer.NodeAnalyzer
import org.derilh.ast.ASTNode
import org.derilh.ast.IdExpressionNode
import org.derilh.core.ValueCategory

class IdExpressionAnalyzer : NodeAnalyzer<IdExpressionNode> {
    override fun analyze(node: IdExpressionNode, ctx: AnalyzeContext): ASTNode {
        var resolved = ctx.resolveSymbolsLocal(node.id, ctx.scope,)
        if (resolved.isEmpty()) {
            resolved = ctx.resolveSymbols(node.id, ctx.scope, true)
        }
        if (resolved.isEmpty()) {
            ctx.error("Could not resolve symbol: ${node.id.toDisplayString()}", node)
            return node;
        }
        val hasNonType = resolved.any { it is DeclSymbol.FunctionDecl || it is DeclSymbol.VariableDecl }
        val filtered = if (hasNonType) {
            resolved.filterNot { it is DeclSymbol.ClassDecl }
        } else {
            resolved
        }

        val functions = filtered.filterIsInstance<DeclSymbol.FunctionDecl>()
        val variables = filtered.filterIsInstance<DeclSymbol.VariableDecl>()

        if (variables.isNotEmpty()) {
            if (variables.size > 1 || functions.isNotEmpty()) {
                ctx.error("Ambiguous symbol: '${node.id}'", node)
                return node
            }
            val variable = variables.first()
            node.decl = variable
            node.resolvedType = variable.type
            node.valueCategory = ValueCategory.LVALUE
            return node
        }

        if (functions.isNotEmpty()) {
            if (functions.size == 1) {
                val fn = functions.first()
                node.decl = fn
                node.resolvedType = fn.signatureType
            } else {
//                val overloadSet = DeclSymbol.OverloadSet(node.id, functions)
//                node.resolvedSymbol = overloadSet
//                node.resolvedType = SemanticType.Overload // Неопределенный тип перегрузки
                TODO("Add support for overloads")
            }
            node.valueCategory = ValueCategory.LVALUE
            return node
        }
        //TODO: add ISO lookup standards
        //TODO: Add overload set
        return node
    }
}