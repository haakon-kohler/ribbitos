package dev.ribbitos.lite

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TranscriptTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val file: File by lazy { File(tmp.root, "transcript.json") }

    private fun user(text: String) = Transcript.Message(Transcript.USER, text)

    @Test
    fun startsEmptyWithAConversationId() {
        val t = Transcript(file)
        assertTrue(t.messages.isEmpty())
        assertTrue(t.conversation.isNotBlank())
    }

    @Test
    fun survivesARestart() {
        val first = Transcript(file)
        first.add(user("hi"))
        first.add(Transcript.Message(Transcript.ASSISTANT, "oops", error = true))
        val second = Transcript(file)
        assertEquals(first.messages, second.messages)
        assertEquals(first.conversation, second.conversation)
    }

    @Test
    fun resetStartsANewConversationOnDiskToo() {
        val t = Transcript(file)
        t.add(user("hi"))
        val old = t.conversation
        t.reset()
        assertTrue(t.messages.isEmpty())
        assertNotEquals(old, t.conversation)
        assertEquals(t.conversation, Transcript(file).conversation)
        assertTrue(Transcript(file).messages.isEmpty())
    }

    @Test
    fun keepsOnlyTheNewestMessages() {
        val t = Transcript(file)
        repeat(Limits.MAX_MESSAGES + EXTRA) { t.add(user("m$it")) }
        assertEquals(Limits.MAX_MESSAGES, t.messages.size)
        assertEquals("m$EXTRA", t.messages.first().text)
    }

    @Test
    fun loadingIsCappedToo() {
        val arr = JSONArray()
        repeat(Limits.MAX_MESSAGES + EXTRA) { arr.put(JSONObject().put("role", "user").put("text", "m$it")) }
        file.writeText(JSONObject().put("conversation", "c").put("messages", arr).toString())
        val t = Transcript(file)
        assertEquals(Limits.MAX_MESSAGES, t.messages.size)
        assertEquals("m$EXTRA", t.messages.first().text)
    }

    @Test
    fun corruptFileStartsFresh() = assertFresh("{not json")

    @Test
    fun missingFieldsStartFresh() = assertFresh("""{"messages":[]}""")

    @Test
    fun unknownRoleStartsFresh() =
        assertFresh("""{"conversation":"c","messages":[{"role":"robot","text":"x"}]}""")

    @Test
    fun oversizedFileStartsFresh() {
        file.writeText(" ".repeat((Limits.MAX_TRANSCRIPT_BYTES + 1).toInt()))
        assertTrue(Transcript(file).messages.isEmpty())
    }

    @Test
    fun blankConversationIdGetsReplaced() {
        file.writeText("""{"conversation":"","messages":[]}""")
        assertTrue(Transcript(file).conversation.isNotBlank())
    }

    @Test
    fun savingLeavesNoTempFile() {
        Transcript(file).add(user("hi"))
        assertFalse(File(file.path + ".tmp").exists())
    }

    @Test
    fun unwritableLocationKeepsWorkingInMemory() {
        val t = Transcript(File(tmp.root, "missing-dir/transcript.json"))
        t.add(user("hi"))
        assertEquals(listOf(user("hi")), t.messages)
    }

    @Test
    fun unknownRoleIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { Transcript.Message("robot", "x") }
    }

    private fun assertFresh(content: String) {
        file.writeText(content)
        val t = Transcript(file)
        assertTrue(t.messages.isEmpty())
        assertTrue(t.conversation.isNotBlank())
    }

    private companion object {
        const val EXTRA = 5
    }
}
