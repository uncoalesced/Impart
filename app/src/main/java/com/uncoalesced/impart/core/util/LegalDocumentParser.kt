// Engineered by uncoalesced
package com.uncoalesced.impart.core.util

import com.uncoalesced.impart.domain.model.LegalBlock
import com.uncoalesced.impart.domain.model.LegalDocument

// Just enough Markdown for the legal documents: headings, paragraphs and bullets. The
// documents are written to that subset on purpose, so the app needs no rendering library.
// Plain text (the Apache license) is split into preformatted blocks at blank lines, which
// lets a lazy list render it without laying out 11,000 words at once.
object LegalDocumentParser {
    private val HEADING = Regex("^(#{1,6})\\s+(.*)$")
    private val LINK = Regex("\\[([^\\]]+)]\\(([^)]+)\\)")
    private val BLANK_LINES = Regex("\\n[ \\t]*\\n")

    fun parse(text: String, format: LegalDocument.Format): List<LegalBlock> = when (format) {
        LegalDocument.Format.MARKDOWN -> markdown(normalize(text))
        LegalDocument.Format.PLAIN -> plain(normalize(text))
    }

    private fun normalize(text: String): String = text.replace("\r\n", "\n").replace('\r', '\n')

    private fun plain(text: String): List<LegalBlock> =
        text.split(BLANK_LINES)
            .map { it.trimIndent().trim('\n') }
            .filter { it.isNotBlank() }
            .map { LegalBlock.Preformatted(it) }

    private fun markdown(text: String): List<LegalBlock> {
        val blocks = mutableListOf<LegalBlock>()
        val paragraph = StringBuilder()
        val bullet = StringBuilder()
        var inBullet = false

        fun flush() {
            if (paragraph.isNotEmpty()) {
                blocks += LegalBlock.Paragraph(inline(paragraph.toString()))
                paragraph.clear()
            }
            if (inBullet) {
                blocks += LegalBlock.Bullet(inline(bullet.toString()))
                bullet.clear()
                inBullet = false
            }
        }

        for (line in stripComments(text).lines()) {
            val trimmed = line.trim()
            val heading = HEADING.find(trimmed)
            when {
                trimmed.isEmpty() -> flush()
                heading != null -> {
                    flush()
                    val level = heading.groupValues[1].length.coerceAtMost(2)
                    blocks += LegalBlock.Heading(level, inline(heading.groupValues[2].trim()))
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    flush()
                    bullet.append(trimmed.drop(2).trim())
                    inBullet = true
                }
                inBullet -> bullet.append(' ').append(trimmed)
                else -> {
                    if (paragraph.isNotEmpty()) paragraph.append(' ')
                    paragraph.append(trimmed)
                }
            }
        }
        flush()
        return blocks
    }

    private fun stripComments(text: String): String {
        val out = StringBuilder()
        var index = 0
        while (index < text.length) {
            val start = text.indexOf("<!--", index)
            if (start < 0) {
                out.append(text, index, text.length)
                break
            }
            out.append(text, index, start)
            val end = text.indexOf("-->", start + 4)
            index = if (end < 0) text.length else end + 3
        }
        return out.toString()
    }

    private fun inline(text: String): String =
        LINK.replace(text) { "${it.groupValues[1]} (${it.groupValues[2]})" }
            .replace("**", "")
            .replace("`", "")
}
