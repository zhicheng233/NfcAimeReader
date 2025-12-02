package org.ohdj.nfcaimereader.libs

import android.nfc.tech.NfcA
import android.util.Log
import java.io.IOException

class NfcAHandler(private val nfcA: NfcA) : INFCHandler {
    private val TAG = "NfcAHandler"

    override fun getAccessCode(): String? {
        return try {
            nfcA.connect()
            // 获取卡片的物理 UID
            val uid = nfcA.tag.id
            // 将字节数组转换为大写 Hex 字符串
            val hexUid = uid.joinToString("") { "%02X".format(it) }
            Log.d(TAG, "Read NfcA UID: $hexUid")
            hexUid
        } catch (e: IOException) {
            Log.e(TAG, "Failed to read NfcA tag", e)
            null
        } finally {
            try {
                nfcA.close()
            } catch (e: Exception) {
                // 忽略关闭时的异常
            }
        }
    }
}