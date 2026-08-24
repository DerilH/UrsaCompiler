package org.derilh.analyzer

import org.derilh.ast.ASTNode
import org.derilh.ast.ClassBodyNode
import org.derilh.ast.ClassDeclarationNode
import org.derilh.ast.ClassDefinitionNode
import org.derilh.ast.CompoundStatementNode
import org.derilh.ast.FunctionDefinitionNode
import org.derilh.ast.IReturnableNode
import org.derilh.ast.ReturnStatementNode
import org.derilh.semantic.SemanticType

class ClassDeclAnalyzer : NodeAnalyzer<ClassDeclarationNode> {
    override fun analyze(node: ClassDeclarationNode, ctx: AnalyzeContext): ASTNode {
        val name = node.name;
        if(name == null) {
            ctx.error("Anonymous classes are not supported yet", node)
            return node;
        }

        ctx.scope.define(DeclSymbol.classDecl(name.name, node, node.type, ctx.scope.ownerSymbol))
        return node;
    }

}

class ClassDefAnalyzer : NodeAnalyzer<ClassDefinitionNode> {
    override fun analyze(node: ClassDefinitionNode, ctx: AnalyzeContext): ASTNode {
        val name = node.name;
        if(name == null) {
            ctx.error("Anonymous classes are not supported yet", node)
            return node;
        }
        val decl = DeclSymbol.classDef(name.name, node, node.type, ctx.scope.ownerSymbol)
        ctx.scope.define(decl)
        ctx.withScope(decl) {
            ctx.analyze(node.body, ctx.scope)
        }
        return node;
    }
}

class ClassBodyAnalyzer : NodeAnalyzer<ClassBodyNode> {
    override fun analyze(
        node: ClassBodyNode,
        ctx: AnalyzeContext
    ): ASTNode {
        for (child in node.declarations) {
            if (child is ClassDeclarationNode) {
                ctx.error("Inner classes are not supported yet", child)
                continue;
            }

            ctx.analyze(child, ctx.scope)
        }

        return node;
    }

}
