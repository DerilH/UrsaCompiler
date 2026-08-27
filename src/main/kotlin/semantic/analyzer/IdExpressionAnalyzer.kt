package org.derilh.semantic.analyzer

import org.derilh.analyzer.AnalyzeContext
import org.derilh.analyzer.DeclSymbol
import org.derilh.analyzer.NodeAnalyzer
import org.derilh.ast.ASTNode
import org.derilh.ast.IdExpressionNode
import org.derilh.core.ValueCategory
import org.derilh.core.getOrElse

class IdExpressionAnalyzer : NodeAnalyzer<IdExpressionNode> {
    override fun analyze(node: IdExpressionNode, ctx: AnalyzeContext): ASTNode {
        val resolved = ctx.resolveSymbols(node.id, ctx.scope).getOrElse { ctx.error(it,node); return node; }

        if (resolved is DeclSymbol.VariableDecl) {
            node.decl = resolved
            node.resolvedType = resolved.type
            node.valueCategory = ValueCategory.LVALUE
            return node
        }

        if (resolved is DeclSymbol.FunctionDecl) {
//            if (functions.size == 1) {
                node.decl = resolved
                node.resolvedType = resolved.signatureType
//
            node.valueCategory = ValueCategory.LVALUE
            return node
        }
//                val overloadSet = DeclSymbol.OverloadSet(node.id, functions)
//                node.resolvedSymbol = overloadSet
//                node.resolvedType = SemanticType.Overload // Неопределенный тип перегрузки
                TODO("Add support for overloads")
//            }
        //TODO: add ISO lookup standards
        //TODO: Add overload set
        return node
    }
}