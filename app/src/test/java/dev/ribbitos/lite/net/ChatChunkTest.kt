package dev.ribbitos.lite.net

import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ChatChunkTest {
    private fun delta(content: String) = """{"choices":[{"delta":{"content":$content}}]}"""

    @Test
    fun textDelta() = assertEquals(ChatChunk.Text("Hi"), ChatChunk.parse(delta("\"Hi\"")))

    @Test
    fun emptyContentIsOther() = assertEquals(ChatChunk.Other, ChatChunk.parse(delta("\"\"")))

    @Test
    fun nullContentIsOther() = assertEquals(ChatChunk.Other, ChatChunk.parse(delta("null")))

    @Test
    fun roleOnlyDeltaIsOther() =
        assertEquals(ChatChunk.Other, ChatChunk.parse("""{"choices":[{"delta":{"role":"assistant"}}]}"""))

    @Test
    fun wholeMessageShapeIsText() =
        assertEquals(ChatChunk.Text("Hi"), ChatChunk.parse("""{"choices":[{"message":{"content":"Hi"}}]}"""))

    @Test
    fun noChoicesIsOther() = assertEquals(ChatChunk.Other, ChatChunk.parse("""{"id":"x"}"""))

    @Test
    fun emptyChoicesIsOther() = assertEquals(ChatChunk.Other, ChatChunk.parse("""{"choices":[]}"""))

    @Test
    fun errorWithMessageFails() =
        assertEquals(ChatChunk.Failed("rate limited"), ChatChunk.parse("""{"error":{"message":"rate limited"}}"""))

    @Test
    fun errorWithoutMessageFailsGenerically() =
        assertEquals(ChatChunk.Failed("Server error"), ChatChunk.parse("""{"error":{}}"""))

    @Test
    fun doneMarkerWithWhitespace() = assertEquals(ChatChunk.Done, ChatChunk.parse("  [DONE] "))

    @Test
    fun garbageIsOther() = assertEquals(ChatChunk.Other, ChatChunk.parse("<html>502</html>"))

    @Test
    fun fullMessageReadsTheReply() =
        assertEquals("Hi", ChatChunk.fullMessage("""{"choices":[{"message":{"content":"Hi"}}]}"""))

    @Test(expected = JSONException::class)
    fun fullMessageRejectsOtherShapes() {
        ChatChunk.fullMessage("""{"nope":true}""")
    }

    @Test
    fun jsonParsesObjectsOnly() {
        assertNotNull(ChatChunk.json("{}"))
        assertNull(ChatChunk.json("[DONE]"))
    }
}
