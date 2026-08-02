package org.derilh

class PreProcessor {
    var line = 0;
    fun preProcess(input: String, fileName: String): String {
        val builder = StringBuilder()
        var i = 0;
        var skipLine: Boolean = false;
        var skipUntil: String? = null;
        while (i < input.length) {
            val ch = input[i]
            val firstTwo = "$ch${input.getOrNull(i + 1)}";
            if(ch == '#' || firstTwo == "//")
                skipLine = true
            else if(firstTwo == "/*") {
                skipUntil = "*/"
                skipLine = true
                i++
            }

            if(ch == '\n') {
                line++;
            }

            if((skipLine && ((ch == '\n' || ch == '\r') && skipUntil == null) || firstTwo == skipUntil)) {
                skipLine = false;
                builder.append("#line $line $fileName")
                if(firstTwo == skipUntil) {
                    i += skipUntil.length
                    skipUntil = null
                    continue
                }
                skipUntil = null
            }

            if(skipLine) {
                i++
                continue
            }

            builder.append(ch)
            i++
        }
        return builder.toString()
    }
}