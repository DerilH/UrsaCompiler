package org.derilh.semantic.analyzer

class ViableCandidate<T>(
    val decl: T,
    val sequences: List<ConversionSequence>
)