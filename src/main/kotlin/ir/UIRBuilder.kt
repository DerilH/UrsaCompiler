package org.derilh.ir

import org.derilh.ast.ASTNode

interface IIRBuilder {
    fun generate(ast: ASTNode)
}

// Load %addr, $reg-> for load from mem to reg
// Store $reg, %addr for store to mem from reg
