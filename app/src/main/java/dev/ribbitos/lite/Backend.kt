package dev.ribbitos.lite

import dev.ribbitos.lite.net.ClaudeBackend
import dev.ribbitos.lite.net.OpenAiBackend
import dev.ribbitos.lite.net.OpenClawBackend
import java.io.IOException
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Something the R1 talks to. This is the extension point: implement it, add a [Provider], and map
 * it in [Wiring.defaultBackend] (or replace [Wiring.backendFactory]). [DemoBackend] is the smallest
 * complete example; [OpenClawBackend], [OpenAiBackend] and [ClaudeBackend] are the real ones.
 */
interface Backend {
    /**
     * Sends [text] as the next message in [conversation] (a stable id; a new chat gets a new one)
     * and streams the reply through [onDelta]. [history] is everything said before, oldest first,
     * for backends whose servers don't keep it (see [History.forTurn]). Runs on a worker thread and
     * returns the whole reply. Every failure, including [cancel], surfaces as an [IOException].
     */
    @Throws(IOException::class)
    fun send(text: String, history: List<Transcript.Message>, conversation: String, onDelta: (String) -> Unit): String

    /** Aborts an in-flight [send] from another thread; that [send] then throws. */
    fun cancel()
}

/** Process-wide wiring: the one place that decides which [Backend] runs, and on which thread. */
object Wiring {
    /** Builds the backend for each turn from the current [Settings]. Replace to change backends. */
    @Volatile
    var backendFactory: (Settings) -> Backend = ::defaultBackend

    /** Runs turns off the main thread, one at a time. */
    @Volatile
    var executor: Executor = Executors.newSingleThreadExecutor()

    /** The built-in backend for each [Provider]. */
    fun defaultBackend(settings: Settings): Backend = when (settings.provider) {
        Provider.DEMO -> DemoBackend()
        Provider.OPENCLAW -> OpenClawBackend(settings)
        Provider.OPENAI -> OpenAiBackend(settings)
        Provider.CLAUDE -> ClaudeBackend(settings)
    }
}

/** The system prompt every backend that takes one sends: the screen is tiny. */
object Style {
    const val SYSTEM = "The user is on a tiny 2.9-inch screen. Reply briefly in plain text, no tables."
}
