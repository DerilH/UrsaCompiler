package org.derilh.analyzer

import org.derilh.exceptions.SemanticProblem


open class Scope(
    val parent: Scope? = null,
    val ownerSymbol: DeclSymbol? = null
) {
    protected val symbolsMap = mutableMapOf<String, MutableSet<DeclSymbol>>()

    val symbols: Collection<DeclSymbol> get() = symbolsMap.values.flatten()
    private val usingDirectives = mutableListOf<Scope>()

    fun define(symbol: DeclSymbol) {
        val set=  symbolsMap.computeIfAbsent(symbol.name) {mutableSetOf()}
        when(symbol) {
            is DeclSymbol.VariableDecl -> {
                set.clear()
                set += symbol
            }
            is DeclSymbol.NamespaceDecl -> throw IllegalStateException("Namespace cannot be redefined: ${symbol.name}")
            is DeclSymbol.ClassDecl -> throw IllegalStateException("Class cannot be redefined: ${symbol.name}")
            is DeclSymbol.FunctionDecl -> set += symbol
        }
    }

    fun addUsingDirective(importedScope: Scope) {
        usingDirectives.add(importedScope)
    }

    open fun lookupLocal(name: String): Set<DeclSymbol> {
        val direct = symbolsMap[name]
        if (direct != null) return direct

        for (imported in usingDirectives) {
            val found = imported.symbolsMap[name]
            if (found != null) return found
        }
        return emptySet()
    }

    fun lookupUnqualified(name: String): Set<DeclSymbol> = lookupLocal(name).ifEmpty { parent?.lookupUnqualified(name) ?: emptySet()}

    fun getRootScope(): Scope = parent?.getRootScope() ?: this
}

class ClassScope(
    parent: Scope?,
    ownerClass: DeclSymbol.ClassDecl,
) : Scope(parent = parent, ownerSymbol = ownerClass) {
    fun lookupMember(name: String): Set<DeclSymbol> {
        val local = symbolsMap[name]
        if (local != null) return local
        //TODO enable member lookup for future inheritance
//        for (base in ownerClass.baseClasses) {
//            val fromBase = base.classScope.lookupMember(name)
//            if (fromBase != null) return fromBase
//        }
        return emptySet()
    }

    override fun lookupLocal(name: String): Set<DeclSymbol> {
        return lookupMember(name)
    }

    fun lookupUnqualifiedInMethod(name: String): Set<DeclSymbol> = lookupMember(name).ifEmpty { parent?.lookupUnqualified(name) ?: emptySet()}
}

class GlobalScope : Scope()