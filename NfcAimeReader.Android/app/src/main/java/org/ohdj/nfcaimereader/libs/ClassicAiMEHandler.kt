import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.util.Log
import org.ohdj.nfcaimereader.libs.INFCHandler

class ClassicAiMEHandler(private val tag: MifareClassic): INFCHandler {

    private val TAG = "ClassicAiMEHandler"

    private val keyHexList = listOf(
        "6090D00632F5","019761AA8082","574343467632","A99164400748",
        "62742819AD7C","CC5075E42BA1","B9DF35A0814C","8AF9C718F23D",
        "58CD5C3673CB","FC80E88EB88C","7A3CDAD7C023","30424C029001",
        "024E4E44001F","ECBBFA57C6AD","4757698143BD","1D30972E6485",
        "F8526D1A8D6D","1300EC8C7E80","F80A65A87FFA","DEB06ED4AF8E",
        "4AD96BF28190","000390014D41","0800F9917CB0","730050555253",
        "4146D4A956C4","131157FBB126","E69DD9015A43","337237F254D5",
        "9A8389F32FBF","7B8FB4A7100B","C8382A233993","7B304F2A12A6",
        "FC9418BF788B"
    )

    private fun hexToKey(hex: String): ByteArray {
        val clean = hex.trim()
        require(clean.length == 12) { "Key hex must be 12 hex chars (6 bytes): $hex" }
        return ByteArray(6) { i ->
            ((clean[i * 2].toString() + clean[i * 2 + 1].toString()).toInt(16) and 0xFF).toByte()
        }
    }

    override fun getAccessCode(): String? {
        var mfc: MifareClassic? = null
        try {
            tag.connect()

            val sectorIndex = 0
            val blockIndex = tag.sectorToBlock(sectorIndex) + 2

            for (keyHex in keyHexList) {
                val keyBytes = try {
                    hexToKey(keyHex)
                } catch (e: Exception) {
                    Log.w(TAG, "invalid key hex: $keyHex")
                    continue
                }

                val authA = try {
                    tag.authenticateSectorWithKeyA(sectorIndex, keyBytes)
                } catch (e: Exception) {
                    Log.w(TAG, "authenticateSectorWithKeyA Exception: ${e.message}")
                    false
                }

                if (authA) {
                    Log.d(TAG, "sector $sectorIndex authenticate with KeyA: $keyHex")
                    val blockData = try {
                        tag.readBlock(blockIndex)
                    } catch (e: Exception) {
                        Log.e(TAG, "Read block failed: ${e.message}")
                        return null
                    }
                    val hex = blockData.toHexString()
                    return hex.takeLastOrAll(20)
                }

                val authB = try {
                    tag.authenticateSectorWithKeyB(sectorIndex, keyBytes)
                } catch (e: Exception) {
                    Log.w(TAG, "authenticateSectorWithKeyB Exception: ${e.message}")
                    false
                }

                if (authB) {
                    Log.d(TAG, "sector $sectorIndex authenticate with KeyB: $keyHex")
                    val blockData = try {
                        tag.readBlock(blockIndex)
                    } catch (e: Exception) {
                        Log.e(TAG, "Read block failed: ${e.message}")
                        return null
                    }
                    val hex = blockData.toHexString()
                    return hex.takeLastOrAll(20)
                }
            }

            Log.w(TAG, "No valid key")
            return null
        } finally {
            try {
                tag?.close()
            } catch (e: Exception) {
                Log.w(TAG, "${e.message}")
            }
        }
    }

    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02X".format(it) }

    private fun String.takeLastOrAll(n: Int): String =
        if (this.length <= n) this else this.substring(this.length - n)
}
