package org.derilh.analyzer

import org.derilh.ast.ASTNode
import org.derilh.ast.ExpressionNode
import org.derilh.ast.VariableDeclarationNode
import org.derilh.core.getOrElse
import org.derilh.core.ifFailure

class VarDeclAnalyzer : NodeAnalyzer<VariableDeclarationNode> {
    override fun analyze(node: VariableDeclarationNode, ctx: AnalyzeContext): ASTNode {
        for (declarator in node.declarations) {
            if (declarator.id == null) {
                ctx.error("Variable declarator must have a name", declarator)
                continue;
            }

            ctx.findAnalyzer(declarator).analyze(declarator, ctx)

            if(declarator.type.resolvedType == null) {
                ctx.error("Cannot resolve type for variable ${declarator.id.name}", declarator)
                continue
            }

            val decl = DeclSymbol.variable(declarator.id.name, declarator, ctx.scope.ownerSymbol)
            ctx.scope.define(decl)

            val init = declarator.initializer;
            if(init == null) {
                ctx.resolveType(declarator.type, ctx.scope).ifFailure(ctx::error)
            } else {
                val declaratorType = declarator.type.resolvedType
                val initType = init.resolvedType

                if(declaratorType == null || initType == null) {
                    ctx.error("Could not resolve type of variable ${declarator.id.name}", declarator)
                    continue
                }

                if (!ctx.isSameType(declaratorType, initType)) {
                    ctx.error(
                        "Cannot initialize variable ${declarator.id.name} of type ${declarator.type.resolvedType} with ${init.resolvedType}",
                        declarator
                    )
                    continue
                }
            }

        }
        return node;
    }
}