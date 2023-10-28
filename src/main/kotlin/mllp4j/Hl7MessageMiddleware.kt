package mllp4j

import ca.uhn.hl7v2.model.Message
import java.util.concurrent.CompletableFuture
import java.util.function.Function
import java.util.stream.Collectors

interface Hl7MessageMiddleware {
    fun handle(message: Hl7Message): CompletableFuture<Message>
}

public class DefaultHl7Middleware(vararg handlers: IHandleIheTransactions) : Hl7MessageMiddleware {
    private val transactionHandlers = handlers.toList().stream().collect(
        Collectors.toMap(
            { t -> t.version + t.handles },
            Function.identity()
        )
    )

    override fun handle(message: Hl7Message): CompletableFuture<Message> {
        val structure = message.message.name
        val handler = transactionHandlers[message.message.version + structure]
        return handler!!.handle(message)
    }
}