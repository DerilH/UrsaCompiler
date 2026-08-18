package org.derilh.analyzer

import org.derilh.ast.ASTNode
import org.derilh.ast.ExpressionNode
import org.derilh.ast.VariableDeclarationNode

class VarDeclAnalyzer : NodeAnalyzer<VariableDeclarationNode> {
    override fun analyze(node: VariableDeclarationNode, ctx: AnalyzeContext): ASTNode {
        for (declarator in node.declarations) {
            if (declarator.id == null) {
                ctx.error("Variable declarator must have a name", declarator)
                continue;
            }

            ctx.findAnalyzer(declarator).analyze(declarator, ctx)

            val decl = DeclSymbol.variable(declarator.id.name, declarator, ctx.scope.ownerSymbol)
            ctx.scope.define(decl)

            val init = declarator.initializer;
            if(init == null) {
                ctx.resolveType(declarator.type, ctx.scope)
            } else {
                declarator.initializer = ctx.findAnalyzer(init).analyze(init, ctx) as ExpressionNode;
                ctx.resolveType(declarator.type, ctx.scope, declarator.initializer?.resolvedType)

                if (!ctx.isSameType(declarator.type.resolvedType!!, init.resolvedType!!)) {
                    ctx.error(
                        "Cannot initialize variable ${declarator.id.name} of type ${declarator.type} with ${init.resolvedType}",
                        declarator
                    )
                    continue
                }
            }

        }
        return node;
    }
}