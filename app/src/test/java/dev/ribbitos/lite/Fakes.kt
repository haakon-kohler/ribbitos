package dev.ribbitos.lite

import java.io.IOException
import java.util.ArrayDeque
import java.util.concurrent.Executor

/** A [Backend] that replies from [script] and records what it was asked. */
class FakeBackend(private val script: FakeBackend.(String, (String) -> Unit) -> String = { _, _ -> "ok" }) : Backend {
    val sent = mutableListOf<Pair<String, String>>()

    @Volatile
    var cancelled = false
        private set

    override fun send(text: String, conversation: String, onDelta: (String) -> Unit): String {
        sent += text to conversation
        return script(text, onDelta)
    }

    override fun cancel() {
        cancelled = true
    }

    /** Throws the way a cancelled OkHttp call does. */
    fun failIfCancelled() {
        if (cancelled) throw IOException("Canceled")
    }
}

/** An [Executor] that holds tasks until [runAll], so tests control when a turn runs. */
class QueueExecutor : Executor {
    private val tasks = ArrayDeque<Runnable>()

    val pending: Int get() = tasks.size

    override fun execute(command: Runnable) {
        tasks.add(command)
    }

    fun runAll() {
        repeat(tasks.size) { tasks.poll()?.run() }
    }
}
