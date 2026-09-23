package org.derilh.semantic

import org.derilh.ast.ExpressionNode
import org.derilh.core.SourceLocation
import org.derilh.core.ValueCategory

data class ExpressionInfo(val type: SemanticType, val valueCategory: ValueCategory, val isNullConstant: Boolean, val location: SourceLocation? = null) {
}
