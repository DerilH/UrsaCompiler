package org.derilh.semantic

import org.derilh.core.TypeInfo
data class FieldLayout(val name: String, val type: SemanticType, val typeInfo: TypeInfo, val offset: Long);
data class MethodLayout(val name: String);

data class StructLayout(val fields: List<FieldLayout>, val methods: List<MethodLayout>, val typeInfo: TypeInfo, val baseClass: SemanticType?)