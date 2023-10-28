package mllp4j

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

interface MessageLog {
    fun write(message: String): Future<Unit>
}

class NullLog private constructor() : MessageLog {
    override fun write(message: String): Future<Unit> {
        return CompletableFuture.completedFuture(Unit)
    }

    companion object {
        private val _instance: MessageLog = NullLog()

        val instance: MessageLog
            get() = _instance
    }
}