package org.derilh.analyzer

import org.derilh.ast.ASTNode
import org.derilh.ast.ClassDeclarationNode
import org.derilh.ast.FunctionBodyNode
import org.derilh.ast.FunctionDefinitionNode
import org.derilh.ast.QualifiedIdentifierNode
import org.derilh.ast.ReturnStatementNode
import org.derilh.core.RefQualifier
import org.derilh.semantic.SemanticType

class FunctionDeclAnalyzer : NodeAnalyzer<FunctionDefinitionNode> {
    private fun analyzeParams(funcDecl: FunctionDefinitionNode, ctx: AnalyzeContext) {
        var hasDefault = false;
        for (param in funcDecl.type.params) {
            if (param.name == null) continue
            if (param.name is QualifiedIdentifierNode) {
                ctx.error("Qualified identifiers are not allowed as parameter names")
                continue
            }

            if(param.declarator.type.resolvedType!!.hasUndeducedAuto) {
                ctx.error("Cannot initialize auto parameter", param)
                continue
            }

            val init = param.declarator.initializer;
            if (init != null) {
                hasDefault = true;
                ctx.findAnalyzer(init).analyze(init, ctx)
            } else if(hasDefault) {
                ctx.error("Missing default value on parameter ${param.name}", node = param)
            }


            ctx.scope.define(DeclSymbol.param(param.name.name, param.declarator, ctx.scope.ownerSymbol))
        }
    }

    override fun analyze(node: FunctionDefinitionNode, ctx: AnalyzeContext): ASTNode {
        ctx.resolveType(node.declarator.type, ctx.scope);

        val defaultParamCount = node.type.params.count {it.declarator.initializer != null}

        val decl = if (ctx.scope is ClassScope) {
            DeclSymbol.method(node.name.name, node, ctx.scope.ownerSymbol, defaultParamCount);
        } else {
            DeclSymbol.function(node.name.name, node, ctx.scope.ownerSymbol, defaultParamCount);
        }
        ctx.scope.define(decl)

        return ctx.withScope(decl) {
            analyzeParams(node, ctx)

            if (!decl.isMethod) {
                val qual = node.type.qualifiers;

                if (qual.isConst || qual.isVolatile || qual.refQualifier != RefQualifier.NONE) {
                    error("Function declaration cannot have cv-qualifiers and ref-qualifiers")
                }
            }

            node.body = ctx.findAnalyzer(node.body).analyze(node.body, ctx) as FunctionBodyNode;
            val bodyRet = node.body.resolveType
            if(bodyRet != null) {
                ctx.resolveType(node.type.returnType, ctx.scope, bodyRet) ?: run {
                    ctx.error("Type resolution failed for function declaration", node)
                    node.type
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



        if(needsDeduce) {
            if(returnType == null) {
                returnType = ctx.types.void
            }
        }
        else {
            if (returnType == null) {
                if(ownerFun.returnType == ctx.types.void) {
                    returnType = ctx.types.void
                }
                else ctx.error("No return statement in function returning non-void")
            }
        }

        node.resolveType = returnType
        return node;
    }
}
