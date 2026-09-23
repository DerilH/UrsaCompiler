package org.derilh.analyzer

import org.derilh.ast.ASTNode
import org.derilh.ast.ExpressionNode
import org.derilh.ast.IdExpressionNode
import org.derilh.ast.MemberAccessExpressionNode
import org.derilh.ast.QualifiedIdentifierNode
import org.derilh.core.Operator
import org.derilh.core.ValueCategory
import org.derilh.core.getOrElse
import org.derilh.semantic.ExpressionInfo
import org.derilh.semantic.SemanticType
import org.derilh.semantic.analyzer.IdContext
import org.derilh.semantic.isDeclared
import org.derilh.semantic.isPointer

class MemberAccessExprAnalyzer : NodeAnalyzer<MemberAccessExpressionNode> {
    override fun analyze(node: MemberAccessExpressionNode, ctx: AnalyzeContext): ASTNode {
        node.left = ctx.analyze(node.left, ctx.scope) as ExpressionNode

        var leftType = node.left.resolvedType ?: return node;
        var canonLeft = leftType.canonical
        val right = node.right;

        when (node.operator) {
            Operator.DOT, Operator.ARROW -> {
                if (!canonLeft.isDeclared()) {
                    if (node.operator == Operator.DOT) {
                        ctx.error("Cannot access member of non-class type", node)
                        return node;
                    } else if ((!canonLeft.isPointer() || !canonLeft.pointee.canonical.isDeclared())) {
                        ctx.error("Cannot access member of non-pointer or non-class type", node)
                        return node;
                    } else {
                        leftType = canonLeft.pointee;
                        canonLeft = leftType.canonical
                    }
                }

                if (right !is IdExpressionNode) {
                    ctx.error("Expected identifier as right expression in member access expression", right)
                    return node;
                }

                val id = right.id;
                if (id is QualifiedIdentifierNode) {
                    ctx.error("Qualified identifier in member access expression is not supported yet", right.id)
                    return node;
                }

                val symbol = (canonLeft as SemanticType.Declared).decl.scope.lookupLocal(id.name, false).getOrElse {ctx.error(it,node); return node;}
                right.decl = symbol;
                if(symbol is DeclSymbol.FunctionOverloadSet) {
                    if(ctx.idContext != IdContext.CALLEE) {
                        ctx.error("Reference to non-static member function must be called", node.right)
                        node.resolvedType = ctx.types.getError();
                        node.valueCategory = ValueCategory.PRVALUE
                        return node;
                    }

                    val boundSet= ctx.types.getBoundMethodSet(ExpressionInfo(node.left.resolvedType!!, node.left.valueCategory!!, false), symbol.name, symbol.overloads.filter { it.isMethod })
                    node.resolvedType = boundSet;
                    node.right.resolvedType = boundSet;

                    node.valueCategory = ValueCategory.LVALUE;
                    right.valueCategory = ValueCategory.LVALUE
                } else if(symbol is DeclSymbol.VariableDecl) {
                    val type = ctx.types.addCV(symbol.type, leftType.isConst, leftType.isVolatile)
                    if (node.left.valueCategory == null) return node;
                    val vc = determineValueCategoryForField(node.left.valueCategory!!, symbol)
                    right.resolvedType = type;
                    node.resolvedType = type;
                    right.valueCategory = vc;
                    node.valueCategory = vc;
                } else ctx.error("Invalid symbol type", right.id)
            }

            Operator.DOT_STAR, Operator.ARROW_STAR -> ctx.error("Operator ${node.operator} is not supported yet", node)
            //TODO: add support for .* and ->* operators
            else -> ctx.error("Invalid operator in member access expression", node)
        }

        return node;
    }

    fun determineValueCategoryForField(
        baseValueCategory: ValueCategory,
        fieldSymbol: DeclSymbol.VariableDecl
    ): ValueCategory {
        val fieldType = fieldSymbol.type.canonical
        if (fieldType is SemanticType.Reference) {
            return ValueCategory.LVALUE
        }

        if (fieldType is SemanticType.RValueReference) {
            return ValueCategory.XVALUE
        }

        return when (baseValueCategory) {
            ValueCategory.LVALUE -> ValueCategory.LVALUE
            ValueCategory.XVALUE -> ValueCategory.XVALUE
            ValueCategory.PRVALUE -> ValueCategory.XVALUE
        }
    }

}
