// Engineered by uncoalesced
package com.uncoalesced.impart.core.util

import com.uncoalesced.impart.domain.model.LegalBlock
import com.uncoalesced.impart.domain.model.LegalDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LegalDocumentParserTest {

    private fun md(text: String) = LegalDocumentParser.parse(text, LegalDocument.Format.MARKDOWN)

    @Test
    fun `headings map to two levels`() {
        val blocks = md("# Title\n\n## Section\n\n### Sub")
        assertEquals(
            listOf(LegalBlock.Heading(1, "Title"), LegalBlock.Heading(2, "Section"), LegalBlock.Heading(2, "Sub")),
            blocks
        )
    }

    @Test
    fun `consecutive lines join into one paragraph`() {
        assertEquals(listOf(LegalBlock.Paragraph("one two three")), md("one\ntwo\n  three\n"))
    }

    @Test
    fun `bullets with continuation lines`() {
        val blocks = md("- first\n  continued\n- second\n\nafter")
        assertEquals(
            listOf(LegalBlock.Bullet("first continued"), LegalBlock.Bullet("second"), LegalBlock.Paragraph("after")),
            blocks
        )
    }

    @Test
    fun `comments are skipped, including multi-line ones`() {
        val blocks = md("<!-- Engineered by uncoalesced -->\n# T\n<!--\nhidden\n-->\ntext")
        assertEquals(listOf(LegalBlock.Heading(1, "T"), LegalBlock.Paragraph("text")), blocks)
    }

    @Test
    fun `inline markup is flattened`() {
        assertEquals(
            listOf(LegalBlock.Paragraph("see the site (https://x.y) and the code now")),
            md("see [the site](https://x.y) and `the code` **now**")
        )
    }

    @Test
    fun `hash without a space is text, not a heading`() {
        assertEquals(listOf(LegalBlock.Paragraph("#hashtag")), md("#hashtag"))
    }

    @Test
    fun `windows line endings and empty input`() {
        assertEquals(listOf(LegalBlock.Paragraph("a b")), md("a\r\nb\r\n"))
        assertTrue(md("").isEmpty())
        assertTrue(md("\n\n<!-- only a comment -->\n").isEmpty())
    }

    @Test
    fun `plain text splits into preformatted blocks at blank lines`() {
        val blocks = LegalDocumentParser.parse("   A\n   b\n\n\n   C\n", LegalDocument.Format.PLAIN)
        assertEquals(listOf(LegalBlock.Preformatted("A\nb"), LegalBlock.Preformatted("C")), blocks)
    }

    // The real documents must stay inside the subset the parser understands, and must name
    // both contact routes. Gradle runs unit tests from the module directory.
    @Test
    fun `shipped documents parse and carry the contacts`() {
        for (name in listOf("TERMS.md", "PRIVACY.md")) {
            val text = File("../$name").readText()
            val blocks = md(text)
            assertTrue("$name has no heading", blocks.first() is LegalBlock.Heading)
            assertTrue("$name is missing the email", text.contains(LegalDocument.CONTACT_EMAIL))
            assertTrue("$name is missing the issues URL", text.contains(LegalDocument.ISSUES_URL))
            assertTrue("$name contains a table the app cannot render", text.lines().none { it.trimStart().startsWith("|") })
        }
        val license = LegalDocumentParser.parse(File("../LICENSE").readText(), LegalDocument.Format.PLAIN)
        assertTrue(license.size > 10)
    }
}
