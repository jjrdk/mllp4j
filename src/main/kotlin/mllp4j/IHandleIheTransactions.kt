package mllp4j

import ca.uhn.hl7v2.model.Message
import java.util.concurrent.CompletableFuture

/**
 * Defines the interface for handling IHE transactions.
 */
public interface IHandleIheTransactions
{
    /**
     * Gets the name of the message structure that is handled.
     */
    val handles:String

    /**
     * Gets the version of the message that is handled.
     */
    val version:String

    /**
     * Handles the received message.
     *
     * @param message The message to handle
     * @return The response message
     */
    fun handle(message: Hl7Message) : CompletableFuture<Message>
}