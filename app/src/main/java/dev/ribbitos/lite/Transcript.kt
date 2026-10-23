package dev.ribbitos.lite

import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * The visible conversation, kept on disk so it survives a restart. The backend keeps the real
 * history; this is only what the screen shows, plus the id that ties both together. Holds at most
 * [Limits.MAX_MESSAGES] messages.
 */
class Transcript(private val file: File) {
    /** One line of the conversation. [role] is [USER] or [ASSISTANT]. */
    data class Message(val role: String, val text: String, val error: Boolean = false) {
        init {
            require(role == USER || role == ASSISTANT) { "Unknown role: $role" }
        }
    }

    /** The id the backend keys this conversation's history off. */
    var conversation: String = newId()
        private set

    private val list = ArrayList<Message>(Limits.MAX_MESSAGES + 1)

    /** Oldest first. */
    val messages: List<Message> get() = list

    init {
        load()
    }

    /** Adds [m], dropping the oldest message past [Limits.MAX_MESSAGES], and saves. */
    fun add(m: Message) {
        list += m
        if (list.size > Limits.MAX_MESSAGES) list.removeAt(0)
        save()
    }

    /** Starts a new conversation: the backend sees a new id, so it starts fresh too. */
    fun reset() {
        conversation = newId()
        list.clear()
        save()
    }

    private fun save() {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("role", it.role).put("text", it.text).put("error", it.error)) }
        val json = JSONObject().put("conversation", conversation).put("messages", arr).toString()
        try {
            val tmp = File(file.path + ".tmp")
            tmp.writeText(json)
            check(tmp.renameTo(file)) { "Couldn't replace ${file.name}" }
        } catch (e: IOException) {
            Log.w(TAG, "Couldn't save the transcript; it lives on in memory", e)
        }
    }

    private fun load() {
        if (!file.isFile || file.length() > Limits.MAX_TRANSCRIPT_BYTES) return
        try {
            val j = JSONObject(file.readText())
            val arr = j.getJSONArray("messages")
            val from = maxOf(0, arr.length() - Limits.MAX_MESSAGES)
            for (i in from until arr.length()) list += parse(arr.getJSONObject(i))
            conversation = j.getString("conversation").ifBlank { newId() }
        } catch (e: JSONException) {
            recover(e)
        } catch (e: IOException) {
            recover(e)
        } catch (e: IllegalArgumentException) {
            recover(e)
        }
    }

    private fun recover(e: Exception) {
        Log.w(TAG, "Unreadable transcript; starting a fresh conversation", e)
        list.clear()
        conversation = newId()
    }

    companion object {
        const val USER = "user"
        const val ASSISTANT = "assistant"
        private const val TAG = "ribbitos"

        private fun newId() = UUID.randomUUID().toString()

        private fun parse(o: JSONObject) = Message(o.getString("role"), o.getString("text"), o.optBoolean("error"))
    }
}
