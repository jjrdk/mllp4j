package mllp4j.tests;

import ca.uhn.hl7v2.model.v251.message.ADT_A01
import ca.uhn.hl7v2.parser.EncodingCharacters
import io.cucumber.java8.En
import mllp4j.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import kotlin.test.assertNotNull

class MllpServerSteps : En {

    init {
        val executor = Executors.newSingleThreadExecutor()
        var server: MllpServer? = null
        var client: MllpClient? = null
        var response: CompletableFuture<Hl7Message>? = null

        Given("^an MLLP server$") {
            server =
                MllpServer(2575, null, DefaultHl7Middleware(TestMessageHandler()), NullLog.instance)
            executor.submit {
                server!!.listen()
            }
        }

        And("^an MLLP client$") {
            client = MllpClient("localhost", 2575, null, null)
        }

        When("^the client sends a message$") {
            val message = ADT_A01()
            message.msh.msh1_FieldSeparator.value = "|"
            message.msh.msh2_EncodingCharacters.value = "^~\\&" //EncodingCharacters.defaultInstance().toString()
            message.msh.msh9_MessageType.msg3_MessageStructure.value = "ADT_A01"
            message.msh.msh12_VersionID.versionID.value = "2.5.1"
            message.msh.messageControlID.value = "123"
            message.pid.insertPid5_PatientName(0).givenName.value = "tester"
            response = client!!.send(message)
        }
        Then("^gets a response from the server$") {
            val completedResponse = response?.join()

            assertNotNull(completedResponse)
            server!!.stop()
            client!!.close()
        }
    }
}
