package mllp4j

class Constants {
    companion object {
        val startCharacter: Char = Char(11)
        val firstEndChar = Char(28)
        val lastEndChar = Char(13)
        val endBlock: ByteArray = byteArrayOf(28, 13)
    }
}