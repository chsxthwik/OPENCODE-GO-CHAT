package com.github.chsxthwik.gochat

import com.github.chsxthwik.gochat.data.Attachment
import com.github.chsxthwik.gochat.data.Conversation
import com.github.chsxthwik.gochat.data.MessageEntity
import com.github.chsxthwik.gochat.data.exportMarkdown
import com.github.chsxthwik.gochat.data.parseAttachments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportTest {

    private fun conv() = Conversation(
        id = "c1", title = "Tokyo trip", model = "glm-5.3-flash",
        createdAt = 0L, updatedAt = 0L,
    )

    private fun msg(role: String, content: String, model: String = "", attachmentsJson: String = "[]") =
        MessageEntity(
            id = "m", conversationId = "c1", role = role, content = content,
            createdAt = 0L, model = model, attachmentsJson = attachmentsJson,
        )

    @Test
    fun `export writes title header and model footer`() {
        val md = exportMarkdown(conv(), listOf(msg("user", "hi")))
        assertTrue(md.startsWith("# Tokyo trip"))
        assertTrue(md.contains("_GoChat · glm-5.3-flash_"))
    }

    @Test
    fun `export labels user as You and assistant by its model`() {
        val md = exportMarkdown(
            conv(),
            listOf(
                msg("user", "question"),
                msg("assistant", "answer", model = "kimi-k3"),
                msg("assistant", "fallback"), // blank model -> conversation model
            ),
        )
        assertTrue(md.contains("**You**:\n\nquestion"))
        assertTrue(md.contains("**kimi-k3**:\n\nanswer"))
        assertTrue(md.contains("**glm-5.3-flash**:\n\nfallback"))
    }

    @Test
    fun `export renders text attachments fenced and images as placeholder`() {
        val atts = """[{"name":"notes.txt","mime":"text/plain","text":"hello file"},{"name":"shot.jpg","mime":"image/jpeg","imageBase64":"data:image/jpeg;base64,xx"}]"""
        val md = exportMarkdown(conv(), listOf(msg("user", "", attachmentsJson = atts)))
        assertTrue(md.contains("```\n// notes.txt\nhello file\n```"))
        assertTrue(md.contains("_[image: shot.jpg]_"))
    }

    @Test
    fun `export skips blank message bodies`() {
        val md = exportMarkdown(conv(), listOf(msg("user", ""), msg("assistant", "real")))
        assertTrue(md.contains("**You**:"))
        assertFalse(md.contains("**You**:\n\n\n"))
    }

    @Test
    fun `parseAttachments tolerates garbage`() {
        assertEquals(emptyList<Attachment>(), parseAttachments("not json"))
        assertEquals(emptyList<Attachment>(), parseAttachments(""))
        val ok = parseAttachments("""[{"name":"a.txt","mime":"text/plain","text":"x"}]""")
        assertEquals(1, ok.size)
        assertEquals("a.txt", ok[0].name)
    }
}
