package org.derilh.semantic.analyzer

import org.derilh.analyzer.AnalyzeContext
import org.derilh.analyzer.ClassScope
import org.derilh.analyzer.DeclSymbol
import org.derilh.analyzer.NodeAnalyzer
import org.derilh.ast.ASTNode
import org.derilh.ast.ClassDeclarationNode
import org.derilh.ast.FunctionBodyNode
import org.derilh.ast.FunctionDeclaratorNode
import org.derilh.ast.FunctionDefinitionNode
import org.derilh.ast.FunctionTypeNode
import org.derilh.ast.ParameterNode
import org.derilh.ast.QualifiedIdentifierNode
import org.derilh.ast.ReturnStatementNode
import org.derilh.ast.VariableDeclaratorNode
import org.derilh.core.MethodQualifiers
import org.derilh.core.RefQualifier
import org.derilh.core.ifFailure
import org.derilh.semantic.SemanticType

private fun analyzeParams(params: List<ParameterNode>, ctx: AnalyzeContext) {
    var hasDefault = false;
    for (param in params) {
        if (param.name == null) continue
        if (param.name is QualifiedIdentifierNode) {
            ctx.error("Qualified identifiers are not allowed as parameter names")
            continue
        }

        ctx.analyze(param.declarator, ctx.scope)

        val init = (param.declarator as? VariableDeclaratorNode)?.initializer;
        if (init != null) {
            hasDefault = true;
            ctx.findAnalyzer(init).analyze(init, ctx)
        } else if (hasDefault) {
            ctx.error("Missing default value on parameter ${param.name}", node = param)
        }


        ctx.scope.define(DeclSymbol.param(param.name.name, param.declarator, ctx.scope.ownerSymbol))
    }
}

private fun checkMethodQualifiers(decl: DeclSymbol.FunctionDecl, qual: MethodQualifiers, ctx: AnalyzeContext) {
    if (!decl.isMethod) {
        if (qual.isConst || qual.isVolatile || qual.refQualifier != RefQualifier.NONE) {
            ctx.error("Function declaration cannot have cv-qualifiers and ref-qualifiers")
        }
    }
}

class FunctionDeclAnalyzer : NodeAnalyzer<FunctionDeclaratorNode> {
    override fun analyze(node: FunctionDeclaratorNode, ctx: AnalyzeContext): ASTNode {
        //TODO: maybe need to check is node.id.name is unqualified-id for declaration only
        ctx.resolveType(node.type, ctx.scope).ifFailure(ctx::error)
        val decl = if (ctx.scope is ClassScope) {
            DeclSymbol.methodDecl(node.id.name, node, ctx.scope.ownerSymbol, node.defaultParamCount);
        } else {
            DeclSymbol.functionDecl(node.id.name, node, ctx.scope.ownerSymbol, node.defaultParamCount);
        }

        ctx.scope.define(decl)
        val type = node.type as FunctionTypeNode
        return ctx.withScope(decl) {
            analyzeParams(type.params, ctx)
            checkMethodQualifiers(decl, type.qualifiers, ctx)
            node;
        }
    }
}

class FunctionDefAnalyzer : NodeAnalyzer<FunctionDefinitionNode> {

    override fun analyze(node: FunctionDefinitionNode, ctx: AnalyzeContext): ASTNode {
        //TODO: maybe need check for redefinitions
        ctx.analyze(node.declarator, ctx.scope)
        val decl = if (ctx.scope is ClassScope) {
            DeclSymbol.methodDef(node.name.name, node, ctx.scope.ownerSymbol, node.declarator.defaultParamCount);
        } else {
            DeclSymbol.functionDef(node.name.name, node, ctx.scope.ownerSymbol, node.declarator.defaultParamCount);
        }

        ctx.scope.define(decl)

        return ctx.withScope(decl) {
            analyzeParams(node.type.params, ctx)
            checkMethodQualifiers(decl, node.type.qualifiers, ctx)

            node.body = ctx.findAnalyzer(node.body).analyze(node.body, ctx) as FunctionBodyNode;
            val bodyRet = node.body.resolveType
            if (bodyRet != null) {
                ctx.resolveType(node.type.returnType, ctx.scope, bodyRet).ifFailure {
                    ctx.error(it)
                }
            }

            node;
        }
    }
}

class FunctionBodyAnalyzer : NodeAnalyzer<FunctionBodyNode> {
    override fun analyze(node: FunctionBodyNode, ctx: AnalyzeContext): ASTNode {
        val ownerFun = ctx.scope.ownerSymbol as DeclSymbol.FunctionDecl;
        val needsDeduce = ownerFun.returnType.hasUndeducedAuto;
        var returnType: SemanticType? = null;

        for (child in node.statements) {
            if (child is FunctionDefinitionNode) {
                ctx.error("Inner function are not supported yet", child)
                continue;
            }
            if (child is ClassDeclarationNode) {
                ctx.error("Inner classes are not supported yet", child)
                continue;
            }

            ctx.findAnalyzer(child).analyze(child, ctx)
            if (child is ReturnStatementNode) {
                if (returnType != null && !ctx.isSameType(returnType, child.expression.resolvedType!!)) {
                    if (needsDeduce) {
                        ctx.error(
                            "'auto' in return type deduced as ${returnType} earlier but here deduced as ${child.expression.resolvedType}",
                            child
                        )
                        returnType = child.expression.resolvedType;
                    } else {
                        ctx.error("Return type ${returnType} is not compatible with ${child.expression.resolvedType}", child)
                    }
                } else {
                    returnType = child.expression.resolvedType;
                }
            }
        }



        if (needsDeduce) {
            if (returnType == null) {
                returnType = ctx.types.void
            }
        } else {
            if (returnType == null) {
                if (ownerFun.returnType == ctx.types.void) {
                    returnType = ctx.types.void
                } else ctx.error("No return statement in function returning non-void")
            }
        }

        node.resolveType = returnType
        return node;
    }
}
