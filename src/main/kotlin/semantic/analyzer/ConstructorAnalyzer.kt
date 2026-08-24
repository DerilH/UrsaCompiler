package org.derilh.semantic.analyzer

import org.derilh.analyzer.AnalyzeContext
import org.derilh.analyzer.ClassScope
import org.derilh.analyzer.DeclSymbol
import org.derilh.analyzer.NodeAnalyzer
import org.derilh.ast.ASTNode
import org.derilh.ast.ConstructorDeclarationNode
import org.derilh.ast.ConstructorDefinitionNode
import org.derilh.ast.FunctionBodyNode
import org.derilh.ast.FunctionTypeNode
import org.derilh.ast.IdExpressionNode
import org.derilh.ast.ParameterNode
import org.derilh.ast.QualifiedIdentifierNode
import org.derilh.ast.StatementNode
import org.derilh.ast.VariableDeclaratorNode
import org.derilh.core.FunctionQualifiers
import org.derilh.core.RefQualifier
import org.derilh.core.ValueCategory
import org.derilh.core.getOrElse
import org.derilh.core.ifFailure
import java.util.logging.Filter

class ConstructorDeclAnalyzer : NodeAnalyzer<ConstructorDeclarationNode> {
    override fun analyze(node: ConstructorDeclarationNode, ctx: AnalyzeContext): ASTNode {
        //TODO: maybe need to check is node.id.name is unqualified-id for declaration only
        ctx.resolveType(node.type, ctx.scope).ifFailure(ctx::error)

        val classDecl = ctx.scope.findCurrentClass();
        if(classDecl == null) {
            ctx.error("Constructor declaration outside of class", node)
            return node;
        }
        val decl = DeclSymbol.constructorDecl(classDecl.name, node, ctx.scope.ownerSymbol, node.defaultParamCount);
        classDecl.scope.define(decl)

        ctx.scope.define(decl)
        val type = node.type;
        return ctx.withScope(decl) {
            var hasDefault = false;
            for (param in node.type.params) {
                ctx.analyze(param, ctx.scope)
                if(hasDefault && !param.hasDefaultValue) {
                    ctx.error("Missing default value on parameter ${param.name}", node = param)
                }
                else hasDefault = hasDefault || param.hasDefaultValue;
            }
            checkMethodQualifiers(decl, type.qualifiers, ctx)
            node;
        }
    }
}

class ConstructorDefAnalyzer : NodeAnalyzer<ConstructorDefinitionNode> {
    override fun analyze(node: ConstructorDefinitionNode, ctx: AnalyzeContext): ASTNode {
        //TODO: maybe need to check is node.id.name is unqualified-id for declaration only
        ctx.resolveType(node.type, ctx.scope).ifFailure(ctx::error)

        val classDecl = ctx.scope.findCurrentClass();
        if(classDecl == null) {
            ctx.error("Constructor declaration outside of class", node)
            return node;
        }
        val decl = DeclSymbol.constructorDef(classDecl.name, node, ctx.scope.ownerSymbol, node.defaultParamCount);
        classDecl.scope.define(decl)

        ctx.scope.define(decl)
        val type = node.type;
        return ctx.withScope(decl) {
            var hasDefault = false;
            for (param in node.type.params) {
                ctx.analyze(param, ctx.scope)
                if(hasDefault && !param.hasDefaultValue) {
                    ctx.error("Missing default value on parameter ${param.name}", node = param)
                }
                else hasDefault = hasDefault || param.hasDefaultValue;
            }

            node.body = ctx.analyze(node.body, ctx.scope) as StatementNode
            ctx.error("Implement constructor member init analyzer", node)
            checkMethodQualifiers(decl, type.qualifiers, ctx)
            node;
        }
    }
}

private fun checkMethodQualifiers(decl: DeclSymbol.FunctionDecl, qual: FunctionQualifiers, ctx: AnalyzeContext) {
    if (!decl.isMethod) {
        if (qual.isConst || qual.isVolatile || qual.refQualifier != RefQualifier.NONE) {
            ctx.error("Function declaration cannot have cv-qualifiers and ref-qualifiers")
        }
    }
}