package mllp4j.tests

import ca.uhn.hl7v2.model.Message
import ca.uhn.hl7v2.model.v251.message.ACK
import ca.uhn.hl7v2.model.v251.message.ADT_A01
import io.cucumber.java8.En
import mllp4j.DefaultHl7Middleware
import mllp4j.Hl7Message
import mllp4j.HandleIheTransactions
import java.util.concurrent.CompletableFuture
import kotlin.test.assertNotNull

class MiddlewareSteps : En {

    init {
        var middleware: DefaultHl7Middleware? = null
        var response: Message? = null

        Given("^a default middleware$") {
            middleware = DefaultHl7Middleware(TestMessageHandler())
        }

        When("^a message is passed$") {
            val msg = ADT_A01()
            msg.initQuickstart("ADT", "A01", "P")
            msg.msh.messageControlID.value = "test"
            response = middleware?.handle(
                Hl7Message(msg, "")
            )?.join()
        }

        Then("^message handler generates response$") {
            assertNotNull(response)
        }
    }
}

class TestMessageHandler : HandleIheTransactions {
    override val handles: String
        get() = "ADT_A01"

    override val version: String
        get() = "2.5.1"

    override fun handle(message: Hl7Message): CompletableFuture<Message> {
        val ack = ACK()
        ack.msh.msh1_FieldSeparator.value = "|"
        ack.msh.msh2_EncodingCharacters.value = "^~\\&" //EncodingCharacters.defaultInstance().toString()
        ack.msh.msh9_MessageType.msg3_MessageStructure.value = "ADT_A01"
        ack.msh.msh12_VersionID.versionID.value = "2.5.1"
        ack.msh.messageControlID.value = "123"
        return CompletableFuture.completedFuture(ack)
    }
}