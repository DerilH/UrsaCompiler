package org.derilh.semantic.analyzer

import org.derilh.analyzer.DeclSymbol
import org.derilh.core.ConversionRank
import org.derilh.core.ConversionKind
import org.derilh.core.ValueCategory
import org.derilh.semantic.SemanticType

interface ConversionSequence {
    var bindsToTemporary: Boolean;
    val outType: SemanticType
}

class IdentityConversionSequence(override val outType: SemanticType) : ConversionSequence {
    override var bindsToTemporary: Boolean = false
};


class UserConversionSequence(
    val firstScs: StdConversionSequence,
    val method: DeclSymbol.FunctionDecl,
    val secondScs: StdConversionSequence,
    override val outType: SemanticType,
    override var bindsToTemporary: Boolean = false,
) : ConversionSequence {
}


class StdConversionSequence(
    val steps: List<ConversionStep>,
    override val outType: SemanticType,
    override var bindsToTemporary: Boolean = false,
) : ConversionSequence {

    fun contains(kind: ConversionKind): Boolean = steps.any { it.kind == kind }

    fun getRank(): ConversionRank {
        var lowestRank = ConversionRank.EXACT_MATCH

        for (kind in steps) {
            if (kind.rank.cost < lowestRank.cost) {
                lowestRank = kind.rank;
            }
        }
        return lowestRank
    }
}

data class ConversionStep(val kind: ConversionKind, val newVC: ValueCategory, val newType: SemanticType) {
    val rank = kind.rank
}