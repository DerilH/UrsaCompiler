//import org.derilh.ast.CharLiteralNode
//import org.derilh.ast.Parser
//import org.derilh.ast.StringConcatExpressionNode
//import org.derilh.ast.StringLiteralNode
//import org.derilh.core.CharPrefix
//import org.derilh.lexer.CharToken
//import org.derilh.lexer.SourceLocation
//import org.derilh.lexer.StringLiteralToken
//import kotlin.test.Test
//import kotlin.test.assertEquals
//import kotlin.test.assertIs
//
//class ParserTest {
//
//    @Test
//    fun `single string literal creates StringLiteralNode`() {
//        val tokens = listOf(
//            StringLiteralToken(value = "hello", prefix = CharPrefix.NONE, location = SourceLocation(0, 0, ""))
//        )
//        val parser = Parser(tokens)
//
//        val ast = parser.parseExpression()
//
//        assertIs<StringLiteralNode>(ast)
//    }
//
//    @Test
//    fun `adjacent string literals create StringConcatExpressionNode`() {
//        val tokens = listOf(
//            StringLiteralToken(value = "hello ", prefix = CharPrefix.NONE, SourceLocation(0, 0, "")),
//            StringLiteralToken(value = "world", prefix = CharPrefix.WIDE, SourceLocation(0, 10, ""))
//        )
//        val parser = Parser(tokens)
//
//        val ast = parser.parseExpression()
//
//        val concatNode = assertIs<StringConcatExpressionNode>(ast)
//        assertEquals(2, concatNode.literals.size)
//    }
//
//    @Test
//    fun `char literal token parses directly to CharLiteralNode`() {
//        val tokens = listOf(
//            CharToken(value = "x", prefix = CharPrefix.UTF16, SourceLocation(0, 0, ""))
//        )
//        val parser = Parser(tokens)
//
//        val ast = parser.parseExpression()
//
//        val charNode = assertIs<CharLiteralNode>(ast)
//        assertEquals("x", charNode.value)
//        assertEquals(CharPrefix.UTF16, charNode.prefix)
//    }
//}