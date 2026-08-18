package org.derilh.semantic

import org.derilh.core.ValueCategory

data class ExpressionInfo(val type: SemanticType, val valueCategory: ValueCategory, val isNullConstant: Boolean)
