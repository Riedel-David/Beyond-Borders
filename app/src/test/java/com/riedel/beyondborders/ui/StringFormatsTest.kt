package com.riedel.beyondborders.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * The compiler checks that a string id exists, not that the arguments handed to it fit
 * its `%d` / `%s` placeholders, and a mismatch is a crash (or a silently dropped
 * argument) on the one device that opens that screen. The host suite never resolves a
 * resource, so this reads the values XML and the call sites as text: every use of an
 * `R.string.x` / `R.plurals.x` with a format call is checked for argument count, and a
 * string literal handed to a `%d` is rejected.
 *
 * Call sites are found in source rather than by constructing every [UiText] in the other
 * tests, which would only cover the cases those tests happen to build.
 */
class StringFormatsTest {

    private class Format(val conversions: Map<Int, Char>) {
        val count get() = conversions.keys.maxOrNull() ?: 0
    }

    private val placeholder = Regex("%(?:(\\d+)\\$)?[-#+ 0,(]*\\d*(?:\\.\\d+)?([a-zA-Z%])")

    private fun parseFormat(text: String): Format {
        val found = mutableMapOf<Int, Char>()
        var next = 1
        for (m in placeholder.findAll(text)) {
            val conv = m.groupValues[2][0]
            if (conv == '%' || conv == 'n') continue
            val index = m.groupValues[1].toIntOrNull() ?: next++
            found[index] = conv
        }
        return Format(found)
    }

    /** name -> formats of every plural item (a plain string has one). */
    private fun loadResources(): Map<String, List<Format>> {
        val out = mutableMapOf<String, List<Format>>()
        val dir = File("src/main/res/values")
        val files = dir.listFiles { f -> f.name.startsWith("strings") && f.name.endsWith(".xml") }!!
        assertTrue("no strings*.xml found under ${dir.absolutePath}", files.isNotEmpty())
        for (file in files) {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            val strings = doc.getElementsByTagName("string")
            for (i in 0 until strings.length) {
                val e = strings.item(i) as Element
                out[e.getAttribute("name")] = listOf(parseFormat(e.textContent))
            }
            val plurals = doc.getElementsByTagName("plurals")
            for (i in 0 until plurals.length) {
                val e = plurals.item(i) as Element
                val items = e.getElementsByTagName("item")
                out[e.getAttribute("name")] = (0 until items.length).map { parseFormat(items.item(it).textContent) }
            }
        }
        return out
    }

    /** Splits the argument list whose '(' is at [open]; null if it uses a spread. */
    private fun arguments(src: String, open: Int): List<String>? {
        val args = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        var i = open + 1
        var inString = false
        while (i < src.length) {
            val c = src[i]
            when {
                inString -> {
                    current.append(c)
                    if (c == '\\') { current.append(src[++i]) } else if (c == '"') inString = false
                }
                c == '"' -> { inString = true; current.append(c) }
                c == '(' || c == '[' || c == '{' -> { depth++; current.append(c) }
                (c == ')' || c == ']' || c == '}') && depth > 0 -> { depth--; current.append(c) }
                c == ')' -> {
                    if (current.isNotBlank()) args += current.toString().trim()
                    return if (args.any { it.startsWith("*") }) null else args
                }
                c == ',' && depth == 0 -> { args += current.toString().trim(); current.clear() }
                else -> current.append(c)
            }
            i++
        }
        return null
    }

    private val callSite = Regex(
        "(UiText\\.Res|UiText\\.Plural|stringResource|pluralStringResource|getString|getQuantityString)" +
            "\\(\\s*(?:id\\s*=\\s*)?R\\.(string|plurals)\\.(\\w+)",
    )

    @Test
    fun `every format call passes as many arguments as its string has placeholders`() {
        val resources = loadResources()
        val problems = mutableListOf<String>()
        var checked = 0
        File("src/main/java").walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            val src = file.readText()
            for (m in callSite.findAll(src)) {
                val kind = m.groupValues[2]
                val name = m.groupValues[3]
                val where = "${file.name}: ${m.groupValues[1]}(R.$kind.$name"
                val formats = resources[name]
                if (formats == null) { problems += "$where) names no resource in strings*.xml"; continue }
                val all = arguments(src, m.range.first + m.groupValues[1].length) ?: continue
                // First entry is the id; a plural's second is the quantity, not a format argument.
                val given = all.drop(if (kind == "plurals") 2 else 1)
                checked++
                val needed = formats.maxOf { it.count }
                if (given.size != needed) {
                    problems += "$where) passes ${given.size} argument(s), the string has $needed placeholder(s)"
                    continue
                }
                given.forEachIndexed { i, arg ->
                    val conversions = formats.mapNotNull { it.conversions[i + 1] }
                    if (arg.startsWith("\"") && conversions.any { it == 'd' }) {
                        problems += "$where) argument ${i + 1} is a string literal for a %d"
                    }
                    if (conversions.size > 1 && conversions.toSet().size > 1 && conversions.toSet() != setOf('d', 's')) {
                        problems += "$where) argument ${i + 1} has conflicting conversions across items: $conversions"
                    }
                }
            }
        }
        assertTrue("only $checked call sites found; has the call-site pattern stopped matching?", checked > 100)
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `placeholders parse the way the platform reads them`() {
        val f = parseFormat("%1\$s of %2\$d, 100%% sure, %s")
        assertTrue(f.conversions == mapOf(1 to 's', 2 to 'd') || f.conversions.keys == setOf(1, 2))
        assertTrue(parseFormat("All %d and %s").conversions == mapOf(1 to 'd', 2 to 's'))
        assertTrue(parseFormat("no arguments, 50%%").count == 0)
    }
}
