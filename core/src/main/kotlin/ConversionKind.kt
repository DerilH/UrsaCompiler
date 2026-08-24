package org.derilh.core

enum class ConversionRank(val cost: Int) {
    EXACT_MATCH(3),
    PROMOTION(2),
    CONVERSION(1),
}

enum class ConversionKind(val rank: ConversionRank, val isImplicit: Boolean = false) {
    NO_OP(ConversionRank.EXACT_MATCH, isImplicit = true),
    QUALIFICATION(ConversionRank.EXACT_MATCH, isImplicit = true),
    LVALUE_TO_RVALUE(ConversionRank.EXACT_MATCH, isImplicit = true),
    ARRAY_TO_POINTER(ConversionRank.EXACT_MATCH, isImplicit = true),
    FUNCTION_TO_POINTER(ConversionRank.EXACT_MATCH, isImplicit = true),
    TEMPORARY_MATERIALIZATION(ConversionRank.EXACT_MATCH, isImplicit = true),
    FUNCTION_PTR_CONVERSION(ConversionRank.EXACT_MATCH, isImplicit = true),

    INTEGRAL_PROMOTION(ConversionRank.PROMOTION, isImplicit = true),
    FLOAT_PROMOTION(ConversionRank.PROMOTION, isImplicit = true),

    INTEGRAL_CONVERSION(ConversionRank.CONVERSION, isImplicit = true),
    FLOAT_CONVERSION(ConversionRank.CONVERSION, isImplicit = true),
    INTEGRAL_TO_FLOAT(ConversionRank.CONVERSION, isImplicit = true),
    FLOAT_TO_INTEGRAL(ConversionRank.CONVERSION, isImplicit = true),
    INTEGRAL_TO_BOOLEAN(ConversionRank.CONVERSION, isImplicit = true),
    POINTER_TO_BOOLEAN(ConversionRank.CONVERSION, isImplicit = true),
    FLOAT_TO_BOOLEAN(ConversionRank.CONVERSION, isImplicit = true),

    NULL_TO_POINTER(ConversionRank.CONVERSION, isImplicit = true),
    POINTER_TO_VOID(ConversionRank.CONVERSION, isImplicit = true),
    POINTER_TO_INTEGRAL(ConversionRank.CONVERSION, isImplicit = false),
    INTEGRAL_TO_POINTER(ConversionRank.CONVERSION, isImplicit = false),

    DERIVED_TO_BASE(ConversionRank.CONVERSION, isImplicit = true),
    BASE_TO_DERIVED(ConversionRank.CONVERSION, isImplicit = false),

    TO_VOID(ConversionRank.CONVERSION, isImplicit = true)
}