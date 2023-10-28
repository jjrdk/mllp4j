package mllp4j

import ca.uhn.hl7v2.model.Message

class Hl7Message(val message: Message, val sourceAddress: String)