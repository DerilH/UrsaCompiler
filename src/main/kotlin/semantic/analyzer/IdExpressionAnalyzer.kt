package org.derilh.semantic.analyzer

import org.derilh.analyzer.AnalyzeContext
import org.derilh.analyzer.DeclSymbol
import org.derilh.analyzer.NodeAnalyzer
import org.derilh.ast.ASTNode
import org.derilh.ast.IdExpressionNode
import org.derilh.ast.QualifiedIdentifierNode
import org.derilh.core.ValueCategory
import org.derilh.core.getOrElse
import org.derilh.semantic.SemanticType

class IdExpressionAnalyzer : NodeAnalyzer<IdExpressionNode> {
    override fun analyze(node: IdExpressionNode, ctx: AnalyzeContext): ASTNode {
        val resolved = ctx.resolveSymbols(node.id, ctx.scope).getOrElse { if(ctx.idContext != IdContext.CALLEE) {ctx.error(it,node)}; null; }

        when (resolved) {
            is DeclSymbol.VariableDecl -> {
                node.decl = resolved
                node.resolvedType = resolved.type
                node.valueCategory = ValueCategory.LVALUE
            }
            is DeclSymbol.FunctionDecl -> {
                node.decl = resolved
                node.resolvedType = ctx.types.getOverloadSet(node.id.name, listOfNotNull(resolved), node.id !is QualifiedIdentifierNode)
                node.valueCategory = ValueCategory.LVALUE
            }
            is DeclSymbol.FunctionOverloadSet -> {
                node.decl = resolved
                node.resolvedType = ctx.types.getOverloadSet(node.id.name, resolved.overloads, node.id !is QualifiedIdentifierNode)
                node.valueCategory = ValueCategory.LVALUE
            }

            is DeclSymbol.ClassDecl -> {
                if(ctx.idContext == IdContext.CALLEE) {
                    val set = resolved.scope.getConstructors().overloads
                    node.resolvedType = ctx.types.getOverloadSet(node.id.name,set, false)
                    node.valueCategory = ValueCategory.PRVALUE
                } else {
//                    node.resolvedType = ctx.types.getDeclared(resolved)
//                    node.valueCategory = ValueCategory.PRVALUE
                    node.resolvedType = ctx.types.getError();
                    node.valueCategory = ValueCategory.PRVALUE
                    ctx.error("Cannot use type name in current context")
                }
            }
            is DeclSymbol.NamespaceDecl -> {
                ctx.error("Cannot use namespace in current context")
            }
            else -> {

                if(node.id !is QualifiedIdentifierNode && ctx.idContext == IdContext.CALLEE) {
                    node.resolvedType = ctx.types.getOverloadSet(node.id.name, emptyList(), true)
                    node.valueCategory = ValueCategory.LVALUE
                }
                else {
                    node.resolvedType = ctx.types.getError();
                    node.valueCategory = ValueCategory.PRVALUE
                }
            }
        }
        //TODO: add ISO lookup standards
        return node
    }
}