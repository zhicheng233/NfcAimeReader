package org.ohdj.nfcaimereader.utils

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.NfcF
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class NfcManager @Inject constructor(
    @ApplicationContext private val context: Context
) : NfcAdapter.ReaderCallback {
    private val TAG = "NfcManager"
    private val nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(context)
    private val applicationScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default) // 应用级作用域

    private val _cardAccessCode = MutableStateFlow<String?>(null)
    val cardAccessCode = _cardAccessCode.asStateFlow()

    private val _nfcState = MutableStateFlow(determineInitialNfcState())
    val nfcState = _nfcState.asStateFlow()

    private var currentActivity: Activity? = null

    private val nfcAdapterStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == NfcAdapter.ACTION_ADAPTER_STATE_CHANGED) {
                val state = intent.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE, NfcAdapter.STATE_OFF)
                _nfcState.value = when {
                    !isNfcSupportedDevice() -> NfcState.UNSUPPORTED
                    state == NfcAdapter.STATE_ON -> NfcState.ENABLED
                    else -> NfcState.DISABLED
                }
            }
        }
    }

    init {
        if (isNfcSupportedDevice()) {
            val filter = IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED)
            // 注册接收器，根据Android版本考虑导出标志
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(
                    nfcAdapterStateReceiver,
                    filter,
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                context.registerReceiver(nfcAdapterStateReceiver, filter)
            }
        }
        // 观察内部NFC状态以管理读卡器模式
        applicationScope.launch {
            _nfcState.collect { state ->
                updateReaderModeForCurrentActivity(state)
            }
        }
    }

    // 确定初始NFC状态
    private fun determineInitialNfcState(): NfcState {
        return when {
            !isNfcSupportedDevice() -> NfcState.UNSUPPORTED
            nfcAdapter?.isEnabled == true -> NfcState.ENABLED
            else -> NfcState.DISABLED
        }
    }

    /**
     * 在Activity的onResume中调用，或者当相关UI变为活动状态时调用
     */
    fun registerActivity(activity: Activity) {
        currentActivity = activity
        updateReaderModeForCurrentActivity(_nfcState.value)
    }

    /**
     * 在Activity的onPause中调用，或者当相关UI变为非活动状态时调用
     */
    fun unregisterActivity(activity: Activity) {
        if (currentActivity == activity) {
            nfcAdapter?.disableReaderMode(activity)
            currentActivity = null
        }
    }

    private fun updateReaderModeForCurrentActivity(currentState: NfcState) {
        val activity = currentActivity ?: return // 只有存在已注册的前台Activity时才操作

        if (currentState == NfcState.ENABLED) {
            nfcAdapter?.enableReaderMode(
                activity,
                this,
                NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, // 跳过 NDEF 格式检查
                null
            )
        } else {
            nfcAdapter?.disableReaderMode(activity)
        }
    }

    override fun onTagDiscovered(tag: Tag?) {
        val idmBytes = tag?.id
        val idm = idmBytes?.joinToString("") { String.format("%02X", it) }
        // _cardIdm.value = idm

        if (tag == null) return

        val nfcF = NfcF.get(tag)
        try {
            nfcF.connect()
            sendPolling(nfcF)
            val response = readSPAD0(nfcF)
            if (response != null) {
                val SPAD0 = response.joinToString("") { String.format("%02X", it) }
                Log.d(TAG, "Got SPAD0: $SPAD0")
                val decryptedAccessCode = FeliCaDecryptor().decrypt(response).joinToString("") { String.format("%02X", it) }.takeLast(20)
                Log.d(TAG, "-> Access Code = $decryptedAccessCode")
                _cardAccessCode.value = decryptedAccessCode
            } else {
                Log.w(TAG, "Failed to read SPAD0")
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            try { nfcF.close() } catch (e: IOException) { }
        }
    }

    private fun sendPolling(nfcF: NfcF, systemCode: Int = 0x88B4): ByteArray? {
        // FeliCa Polling packet:
        // [0] length
        // [1] command code (0x00)
        // [2..3] system code (big endian)
        // [4] request code (0x01 = system code request)
        // [5] time slot (0x0F = max)

        val buffer = ByteBuffer.allocate(6)
        buffer.put(0x06) // length
        buffer.put(0x00) // polling command
        buffer.putShort(systemCode.toShort()) // big endian for system code
        buffer.put(0x01) // request code
        buffer.put(0x0F) // time slot

        val packet = buffer.array()
        return try {
            nfcF.transceive(packet)
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    private fun readSPAD0(nfcF: NfcF): ByteArray? {
        val idm = nfcF.tag.id
        val serviceCode = 0x000B // Scratch Pad service code
        val blockNumber = 0

        // FeliCa Read Without Encryption command structure:
        // [0] Length
        // [1] Command code (0x06)
        // [2..9] IDm
        // [10] Service count
        // [11..12] Service code (little endian)
        // [13] Block count
        // [14..] Block list elements (2 bytes each)

        val buffer = ByteBuffer.allocate(16)
        buffer.put(0x00) // Placeholder for length
        buffer.put(0x06) // Read Without Encryption
        buffer.put(idm)
        buffer.put(0x01) // Number of service codes
        buffer.putShort(serviceCode.toShort().reverseBytes()) // Little endian
        buffer.put(0x01) // Number of blocks
        buffer.put(0x80.toByte()) // Block list element: 0x80 = direct block number
        buffer.put(blockNumber.toByte())

        val packet = buffer.array()
        packet[0] = packet.size.toByte()

        val response = nfcF.transceive(packet)
        // response[1] == 0x07 is normal response
        return if (response.isNotEmpty() && response[1] == 0x07.toByte()) {
            // Data starts after length(1) + response code(1) + IDm(8) + status flag1(1) + status flag2(1) + block count(1)
            // = 13 bytes
            response.copyOfRange(13, response.size)
        } else {
            null
        }
    }

    private fun Short.reverseBytes(): Short {
        val i = this.toInt()
        val hi = (i shr 8) and 0xFF
        val lo = i and 0xFF
        return ((lo shl 8) or hi).toShort()
    }
    /**
     * 检查设备是否具有NFC硬件。
     * @return 如果设备支持NFC则返回true，否则返回false。
     */
    fun isNfcSupportedDevice(): Boolean = nfcAdapter != null

    // 可选：如果需要取消注册广播接收器，添加清理方法
    // 例如，在应用完全关闭或用户设置禁用NFC功能时
    // fun cleanup() {
    //     if (isNfcSupportedDevice()) {
    //         try {
    //             context.unregisterReceiver(nfcAdapterStateReceiver)
    //         } catch (e: IllegalArgumentException) {
    //             // 接收器未注册，忽略
    //         }
    //     }
    //     applicationScope.cancel() // 取消协程
    // }
}