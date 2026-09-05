package org.derilh.core

enum class Keyword(val value: String, val isType: Boolean = false) {
    RETURN("return"),//
    IF("if"),//
    WHILE("while"),//
    FOR("for"),//
    SWITCH("switch"),
    CASE("case"),
    BREAK("break"),//
    CONTINUE("continue"),//
    ASM("asm"),//
    PRIVATE("private"),//
    PROTECTED("protected"),//
    PUBLIC("public"),//
    CLASS("class"),//
    AUTO("auto"),//
    CONST("const",),//
    VOLATILE("volatile"),//
    CATCH("catch"),
    CHAR("char", true),//
    DECLTYPE("decltype"),
    DEFAULT("default"),
    DO("do"),//
    DOUBLE("double", true),//
    FLOAT("float", true),//
    DYNAMIC_CAST("dynamic_cast"),
    ELSE("else"),//
    ENUM("enum"),
    FALSE("false"),//
    TRUE("true"),//
    GOTO("goto"),
    BOOL("bool", true),//
    INT("int", true),//
    LONG("long", true),//
    NAMESPACE("namespace"), //
    NEW("new"), //
    NULLPTR("nullptr"),
    SHORT("short", true), //
    SIZEOF("sizeof"), //
    STATIC_CAST("static_cast"),
    STRUCT("struct"), //
    THIS("this"), //
    TYPEDEF("typedef"),
    VOID("void", true),//
    UNSIGNED("unsigned", true),//
    SIGNED("signed", true),//
    NOEXCEPT("noexcept"),
    CHAR8_T("char8_t", isType = true),
    CHAR16_T("char16_t", isType = true),
    CHAR32_T("char32_t", isType = true),
    WCHAR_T("wchar_t", isType = true),
    //RESERVED
    ALIGNAS("alignas"),
    ALIGNOF("alignof"),
    CONCEPT("concept"),
    CONSTEVAL("consteval"),
    CONSTEXPR("constexpr"),
    CONSTINIT("constinit"),
    CONST_CAST("const_cast"),
    CO_AWAIT("co_await"),
    CO_RETURN("co_return"),
    CO_YIELD("co_yield"),
    DELETE("delete"),
    EXPLICIT("explicit"),
    EXPORT("export"),
    EXTERN("extern"),
    FRIEND("friend"),
    INLINE("inline"),
    MUTABLE("mutable"),
    OPERATOR("operator"),
    REGISTER("register"),
    REINTERPRET_CAST("reinterpret_cast"),
    REQUIRES("requires"),
    STATIC("static"),
    STATIC_ASSERT("static_assert"),
    TEMPLATE("template"),
    THREAD_LOCAL("thread_local"),
    THROW("throw"),
    TRY("try"),
    TYPEID("typeid"),
    TYPENAME("typename"),
    UNION("union"),
    USING("using"),
    VIRTUAL("virtual");
}