package org.derilh.analyzer

import org.derilh.core.OpResult
import org.derilh.core.AccessSpecifier
import org.derilh.core.isSuccess
import org.derilh.core.orElse
import org.derilh.util.ErrorHelper


enum class LookResult {
    AMBIGUOUS,
    NOT_FOUND,
    FOUND
}

open class Scope(
    val parent: Scope?,
    val ownerSymbol: DeclSymbol? = null
) {
    /**
     * Ordinary symbols are used to store variables, functions etc.
     */
    protected val ordinaryMap = mutableMapOf<String, DeclSymbol>()
    val ordinarySymbols: Collection<DeclSymbol> get() = ordinaryMap.values

    /**
     * Tags are used to store class and namespace declarations.
     */
    protected val tagMap = mutableMapOf<String, DeclSymbol>()
    val tagSymbols: Collection<DeclSymbol> get() = tagMap.values

    private val usingDirectives = mutableListOf<Scope>()


    /**
     * Defines a symbol in this scope.
     *
     * @return The resolved symbol depending on its declaration type:
     * - **Function** — returns [DeclSymbol.FunctionOverloadSet]
     * - **Namespace** — returns the first namespace declaration in this scope
     * - **Else** — returns the [OpResult.Success] with symbol itself or an [OpResult.Failure] if it already exists
     */
    open fun define(symbol: DeclSymbol): OpResult<DeclSymbol> {
        val name = symbol.name

        when (symbol) {
            is DeclSymbol.ClassDecl -> {
                val existingTag = tagMap[name]
                if (existingTag != null) {
                    return ErrorHelper.alreadyDefined(symbol, existingTag)
                }
                val existingOrdinary = ordinaryMap[name]
                if (existingOrdinary is DeclSymbol.NamespaceDecl) {
                    return ErrorHelper.alreadyDefined(symbol, existingOrdinary)
                }

                tagMap[name] = symbol
            }


            is DeclSymbol.FunctionDecl -> {
                var existingOrdinary = ordinaryMap[name]
                if (existingOrdinary == null) {
                    existingOrdinary = DeclSymbol.FunctionOverloadSet(name, symbol.parentSymbol)
                    ordinaryMap[name] = existingOrdinary;
                } else if (existingOrdinary !is DeclSymbol.FunctionOverloadSet) {
                    return ErrorHelper.alreadyDefined(symbol, existingOrdinary)
                }

                existingOrdinary.overloads += symbol
                return OpResult.success(existingOrdinary)
            }

            is DeclSymbol.VariableDecl -> {
                val existingOrdinary = ordinaryMap[name]
                if (existingOrdinary != null) {
                    return ErrorHelper.alreadyDefined(symbol, existingOrdinary)
                }

                ordinaryMap[name] = symbol
            }

            is DeclSymbol.NamespaceDecl -> {
                val existingTag = tagMap[name]
                if (existingTag != null) {
                    return ErrorHelper.alreadyDefined(symbol, existingTag)
                }

                val existingOrdinary = ordinaryMap[name]
                if (existingOrdinary != null) {
                    return if (existingOrdinary is DeclSymbol.NamespaceDecl) {
                        OpResult.success(existingOrdinary)
                    } else {
                        ErrorHelper.alreadyDefined(symbol, existingOrdinary)
                    }
                }

                ordinaryMap[name] = symbol
            }

            else -> throw IllegalArgumentException("Invalid symbol type ${symbol.javaClass.name}")
        }

        return OpResult.success(symbol)
    }

    fun findCurrentFunction(): DeclSymbol.FunctionDecl? {
        return if (this.ownerSymbol is DeclSymbol.FunctionDecl) ownerSymbol else parent?.findCurrentFunction()
    }

    fun findCurrentClass(): DeclSymbol.ClassDecl? {
        return if (this.ownerSymbol is DeclSymbol.ClassDecl) ownerSymbol else parent?.findCurrentClass()
    }

    fun addUsingDirective(importedScope: Scope) {
        usingDirectives.add(importedScope)
    }

    open fun lookupLocal(name: String, processedOnly: Boolean): OpResult<DeclSymbol> {
        val ordinary = ordinaryMap[name]
        if (ordinary != null && (!processedOnly || ordinary.processed)) return OpResult.success(ordinary)

        val tag = tagMap[name]
        if (tag != null && (!processedOnly || tag.processed)) return OpResult.success(tag)

        val fromUsing = lookupInUsingNamespaces(name, processedOnly)
        return fromUsing
    }

    private fun Scope.lookupInUsingNamespaces(name: String, processedOnly: Boolean): OpResult<DeclSymbol> {
        var candidate: OpResult.Success<DeclSymbol>? = null

        for (ns in usingDirectives) {
            val found = ns.lookupLocal(name, processedOnly)
            if (!found.isSuccess()) continue;

            if (candidate != null && candidate.value != found) {
                return OpResult.failure("Reference to '$name' is ambiguous", LookResult.AMBIGUOUS)
            }
            candidate = found
        }

        return candidate ?: OpResult.failure("No declaration found for '$name'", LookResult.NOT_FOUND)
    }

    fun lookupUnqualified(name: String, processedOnly: Boolean): OpResult<DeclSymbol> =
        lookupLocal(name, processedOnly).orElse { return parent?.lookupUnqualified(name, processedOnly) ?: it };

    fun getRootScope(): Scope = parent?.getRootScope() ?: this
}

class ClassScope(
    parent: Scope?,
    ownerClass: DeclSymbol.ClassDecl,
) : Scope(parent = parent, ownerSymbol = ownerClass) {
    //TODO: Change default spec for anonymous classes
    private var currentAccessSpecifier: AccessSpecifier = ownerClass.getDefaultVisibility() ?: AccessSpecifier.PUBLIC
    private var ctors = DeclSymbol.FunctionOverloadSet(ownerClass.name, ownerClass);

    override fun define(symbol: DeclSymbol): OpResult<DeclSymbol> {
        if (symbol is DeclSymbol.ConstructorDecl) {
            ctors.overloads += symbol;
            return OpResult.success(symbol);
        } else {
            symbol.accessSpecifier = currentAccessSpecifier;
            return super.define(symbol)
        }
    }

//    fun lookupMember(name: String): OpResult<DeclSymbol> {
//        val local = ordinaryMap[name]
//        if (local != null) return local
//        TODO enable member lookup for future inheritance
//        for (base in ownerClass.baseClasses) {
//            val fromBase = base.classScope.lookupMember(name)
//            if (fromBase != null) return fromBase
//        }
//        return
//        return lookupLocal(name, false)
//    }

//    override fun lookupLocal(name: String, processedOnly: Boolean): OpResult<DeclSymbol> {
//        return lookupMember(name)
//    }

    fun setAccessSpecifier(specifier: AccessSpecifier) {
        currentAccessSpecifier = specifier
    }

    fun getConstructors(): DeclSymbol.FunctionOverloadSet = ctors;
}

class GlobalScope : Scope(null)