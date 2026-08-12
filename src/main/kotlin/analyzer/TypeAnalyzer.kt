package org.derilh.analyzer

import org.derilh.ast.ASTNode
import org.derilh.ast.ArrayTypeNode
import org.derilh.ast.DeclaredTypeNode
import org.derilh.ast.PointerTypeNode
import org.derilh.ast.PrimitiveTypeNode
import org.derilh.ast.RValueReferenceTypeNode
import org.derilh.ast.ReferenceTypeNode
import org.derilh.ast.TypeNode
import org.derilh.core.PrimitiveTypeKind
import org.derilh.exceptions.SemanticException

class PointerTypeAnalyzer : NodeAnalyzer<PointerTypeNode> {
    override fun analyze(node: PointerTypeNode, ctx: AnalyzeContext): ASTNode {
        return ctx.findAnalyzer(node.type).analyze(node.type, ctx)
    }
}

class RefTypeAnalyzer : NodeAnalyzer<ReferenceTypeNode> {
    override fun analyze(node: ReferenceTypeNode, ctx: AnalyzeContext): ASTNode {
        ensureReferenceBase(node.type, node)
        return ctx.findAnalyzer(node.type).analyze(node.type, ctx)
    }
}

class RRefTypeAnalyzer : NodeAnalyzer<RValueReferenceTypeNode> {
    override fun analyze(node: RValueReferenceTypeNode, ctx: AnalyzeContext): ASTNode {
        ensureReferenceBase(node.type, node)
        return ctx.findAnalyzer(node.type).analyze(node.type, ctx)
    }
}

private fun ensureReferenceBase(base: TypeNode, node: TypeNode) {
    if (base is RValueReferenceTypeNode || base is ReferenceTypeNode) {
        throw SemanticException("Reference to reference is forbidden", node)
    }

    if (base is PrimitiveTypeNode && base.kind == PrimitiveTypeKind.VOID) {
        throw SemanticException("Reference to void type is forbidden", node)
    }
}

class PrimitiveTypeAnalyzer : NodeAnalyzer<PrimitiveTypeNode> {
    override fun analyze(node: PrimitiveTypeNode, ctx: AnalyzeContext): ASTNode {
        return node;
    }
}

class ArrayTypeAnalyzer : NodeAnalyzer<ArrayTypeNode> {
    override fun analyze(node: ArrayTypeNode, ctx: AnalyzeContext): ASTNode {
        ctx.findAnalyzer(node.elementType).analyze(node.elementType, ctx)
        if(node.sizeExpression != null) {
            ctx.findAnalyzer(node.sizeExpression).analyze(node.sizeExpression, ctx)
            if(node.sizeExpression.evaluated == null)
                throw SemanticException(
                    "Array size expression must be constant, variable length arrays are not supported by standard.",
                    node.sizeExpression
                )
        }

        return node;
    }
}

class DeclaredTypeAnalyzer : NodeAnalyzer<DeclaredTypeNode> {
    override fun analyze(node: DeclaredTypeNode, ctx: AnalyzeContext): ASTNode {
        val decl = ctx.scope.resolve(node.typeName);


        return ctx.findAnalyzer(node).analyze(node, ctx)
    }
}