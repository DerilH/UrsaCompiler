package org.derilh.analyzer

import org.derilh.core.OpResult
import org.derilh.core.AccessSpecifier


open class Scope(
    val parent: Scope? = null,
    val ownerSymbol: DeclSymbol? = null
) {
    protected val symbolsMap = mutableMapOf<String, MutableSet<DeclSymbol>>()

    private val usingDirectives = mutableListOf<Scope>()

    open fun define(symbol: DeclSymbol): OpResult<Unit> {
        val set= symbolsMap.computeIfAbsent(symbol.name) {mutableSetOf()}

        if (set.isEmpty() || symbol is DeclSymbol.FunctionDecl) {
            set += symbol
            return OpResult.success(Unit)
        }
        else {
            return when (symbol) {
                is DeclSymbol.VariableDecl -> OpResult.failure("Local variable ${symbol.name} already declared or defined", symbol.astNode)
                is DeclSymbol.NamespaceDecl -> OpResult.failure("Namespace ${symbol.name} already defined in this scope", symbol.astNode)
                is DeclSymbol.ClassDecl -> OpResult.failure("Class ${symbol.name} already defined in this scope", symbol.astNode)
            }
        }
    }
    fun findCurrentFunction(): DeclSymbol.FunctionDecl? {
        return if(this.ownerSymbol is DeclSymbol.FunctionDecl) ownerSymbol else parent?.findCurrentFunction()
    }
    fun findCurrentClass(): DeclSymbol.ClassDecl? {
        return if(this.ownerSymbol is DeclSymbol.ClassDecl) ownerSymbol else parent?.findCurrentClass()
    }

    fun addUsingDirective(importedScope: Scope) {
        usingDirectives.add(importedScope)
    }

    open fun lookupLocal(name: String, processedOnly: Boolean): Set<DeclSymbol> {
        val direct = symbolsMap[name]?.filter { if (processedOnly) it.processed else true }?.toSet()
        if (!direct.isNullOrEmpty()) return direct

        for (imported in usingDirectives) {
            val found = imported.symbolsMap[name]?.filter { if (processedOnly) it.processed else true }?.toSet()
            if (!found.isNullOrEmpty()) return found
        }
        return emptySet()
    }

    fun lookupUnqualified(name: String, processedOnly: Boolean): Set<DeclSymbol> = lookupLocal(name,processedOnly).ifEmpty { parent?.lookupUnqualified(name,processedOnly) ?: emptySet()}

    fun getRootScope(): Scope = parent?.getRootScope() ?: this
}

class ClassScope(
    parent: Scope?,
    ownerClass: DeclSymbol.ClassDecl?,
) : Scope(parent = parent, ownerSymbol = ownerClass) {
    //TODO: Change default spec for anonymous classes
    private var currentAccessSpecifier: AccessSpecifier = ownerClass?.getDefaultVisibility() ?: AccessSpecifier.PUBLIC
    private val symbolsByVisibilityMap = mutableMapOf<String, Map<AccessSpecifier, MutableSet<DeclSymbol>>>()

    override fun define(symbol: DeclSymbol): OpResult<Unit> {
        val map = symbolsByVisibilityMap.computeIfAbsent(symbol.name) {mutableMapOf()} as MutableMap<AccessSpecifier, MutableSet<DeclSymbol>>
        val set = map.computeIfAbsent(currentAccessSpecifier) {mutableSetOf()}
        val set1 = symbolsMap.computeIfAbsent(symbol.name) {mutableSetOf()}

        if (set.isEmpty() || symbol is DeclSymbol.FunctionDecl) {
            set += symbol
            set1 += symbol
            return OpResult.success(Unit)
        }
        else {
            return when (symbol) {
                is DeclSymbol.VariableDecl -> OpResult.failure("Local variable ${symbol.name} already declared or defined", symbol.astNode)
                is DeclSymbol.NamespaceDecl -> OpResult.failure("Namespace ${symbol.name} already defined in this scope", symbol.astNode)
                is DeclSymbol.ClassDecl -> OpResult.failure("Namespace ${symbol.name} already defined in this scope", symbol.astNode)
            }
        }
    }

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

    override fun lookupLocal(name: String, processedOnly: Boolean): Set<DeclSymbol> {
        return lookupMember(name)
    }

    fun setAccessSpecifier(specifier: AccessSpecifier) {
        currentAccessSpecifier = specifier
    }
}

class GlobalScope : Scope()