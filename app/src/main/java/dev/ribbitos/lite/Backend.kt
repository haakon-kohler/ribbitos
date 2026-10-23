package dev.ribbitos.lite

import dev.ribbitos.lite.net.OpenClawBackend
import java.io.IOException
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Something the R1 talks to. This is the extension point: implement it and point
 * [Wiring.backendFactory] at your implementation to make ribbitOS talk to Hermes, a local model,
 * or anything else. [OpenClawBackend] is the reference implementation.
 */
interface Backend {
    /**
     * Sends [text] as the next message in [conversation] (a stable id; a new chat gets a new one)
     * and streams the reply through [onDelta]. Runs on a worker thread and returns the whole reply.
     * Keeping the conversation's history is the backend's job. Every failure, including [cancel],
     * surfaces as an [IOException].
     */
    @Throws(IOException::class)
    fun send(text: String, conversation: String, onDelta: (String) -> Unit): String

    /** Aborts an in-flight [send] from another thread; that [send] then throws. */
    fun cancel()
}

/** Process-wide wiring: the one place that decides which [Backend] runs, and on which thread. */
object Wiring {
    /** Builds the backend for each turn from the current [Settings]. Replace to change backends. */
    @Volatile
    var backendFactory: (Settings) -> Backend = ::defaultBackend

    /** [DemoBackend] for the demo URL, otherwise [OpenClawBackend]. */
    fun defaultBackend(settings: Settings): Backend =
        if (settings.demo) DemoBackend() else OpenClawBackend(settings)

    /** Runs turns off the main thread, one at a time. */
    @Volatile
    var executor: Executor = Executors.newSingleThreadExecutor()
}
