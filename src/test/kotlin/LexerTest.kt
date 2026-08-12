import org.derilh.core.CharPrefix
import org.derilh.exceptions.LexerException
import org.derilh.lexer.CharToken
import org.derilh.lexer.Lexer
import org.derilh.lexer.StringLiteralToken
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class LexerTest {


    @Test
    fun `tokenize simple char literal without prefix`() {
        val input = "'a'"
        val lexer = Lexer()

        val tokens = lexer.tokenize(input)

        assertEquals(1, tokens.size)
        val token = assertIs<CharToken>(tokens[0])
        assertEquals(CharPrefix.NONE, token.prefix)
        assertEquals("a", token.value)
    }

    @Test
    fun `tokenize char literals with all prefixes`() {
        val cases = listOf(
            "u8'a'" to CharPrefix.UTF8,
            "u'b'"  to CharPrefix.UTF16,
            "U'c'"  to CharPrefix.UTF32,
            "L'd'"  to CharPrefix.WIDE
        )

        for ((input, expectedPrefix) in cases) {
            val lexer = Lexer()
            val token = assertIs<CharToken>(lexer.tokenize(input)[0])

            assertEquals(expectedPrefix, token.prefix, "Failed on input: $input")
        }
    }

    @Test
    fun `tokenize char literal with escape sequences`() {
        val input = "'\\n'"
        val lexer = Lexer()

        val token = assertIs<CharToken>(lexer.tokenize(input)[0])
        assertEquals(CharPrefix.NONE, token.prefix)
        assertEquals("\n", token.value)
    }

    @Test
    fun `tokenize escaped single quote char literal`() {
        val input = "'\\''"
        val lexer = Lexer()

        val token = assertIs<CharToken>(lexer.tokenize(input)[0])
        assertEquals("'", token.value)
    }

    @Test
    fun `tokenize string literal with u8 prefix`() {
        val input = "u8\"Hello\\nWorld\""
        val lexer = Lexer()

        val tokens = lexer.tokenize(input)

        assertEquals(1, tokens.size)
        val token = assertIs<StringLiteralToken>(tokens[0])
        assertEquals(CharPrefix.UTF8, token.prefix)
        assertEquals("Hello\nWorld", token.value)
    }

    @Test
    fun `tokenize wide string literal`() {
        val input = "L\"Wide text\""
        val lexer = Lexer()

        val token = assertIs<StringLiteralToken>(lexer.tokenize(input)[0])
        assertEquals(CharPrefix.WIDE, token.prefix)
        assertEquals("Wide text", token.value)
    }

    @Test
    fun `test valid escapes`() {
        assertEquals(10, decode("\\n"))
        assertEquals(65, decode("\\101"))
        assertEquals(65, decode("\\x41"))
        assertEquals(0x0410, decode("\\u0410"))
        assertEquals(0x1F600, decode("\\U0001F600"))
    }

    @Test
    fun `test invalid hex throws exception`() {
        assertThrows<LexerException> { decode("\\x") }
        assertThrows<LexerException> { decode("\\x123456789") }
    }

    @Test
    fun `test invalid unicode length throws exception`() {
        assertThrows<LexerException> { decode("\\u123") }
        assertThrows<LexerException> { decode("\\U1234567") }
    }

    private fun decode(inputCode: String): Int {
        val lexer = Lexer()
        return lexer.decodeEscapeChar('"')
    }
}