package org.ohdj.nfcaimereader.utils

import ClassicAiMEHandler
import FeliCaHandler
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.nfc.tech.NfcF
import android.os.Build
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ohdj.nfcaimereader.data.datastore.FelicaPreferenceViewModel
import org.ohdj.nfcaimereader.data.datastore.felicaDataStore
import org.ohdj.nfcaimereader.libs.INFCHandler
import org.ohdj.nfcaimereader.libs.NfcAHandler
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
                NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, // 跳过 NDEF 格式检查
                null
            )
        } else {
            nfcAdapter?.disableReaderMode(activity)
        }
    }

    override fun onTagDiscovered(tag: Tag?) {
        if (tag == null) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = context.felicaDataStore.data.first()
                val felicaCompatibility = prefs[booleanPreferencesKey("felica_compatibility_mode")] ?: true

                val techList = tag.techList?.toList() ?: emptyList()
                var handler: INFCHandler? = null

                // 1. 优先检查 FeliCa (NfcF)
                if (techList.contains(android.nfc.tech.NfcF::class.java.name)) {
                    val nfcF = NfcF.get(tag)
                    if (nfcF != null) {
                        handler = FeliCaHandler(nfcF, felicaCompatibility)
                    }
                }
                // 2. 其次检查 MifareClassic
                // 注意：必须先检查这个，因为 MifareClassic 也是 NfcA 的一种
                else if (techList.contains(android.nfc.tech.MifareClassic::class.java.name)) {
                    val mifareClassic = MifareClassic.get(tag)
                    if (mifareClassic != null) {
                        handler = ClassicAiMEHandler(mifareClassic)
                    }
                }
                // 3. 最后检查通用的 NfcA
                else if (techList.contains(android.nfc.tech.NfcA::class.java.name)) {
                    val nfcA = android.nfc.tech.NfcA.get(tag)
                    if (nfcA != null) {
                        handler = NfcAHandler(nfcA)
                    }
                }

                // 执行 Handler 获取 AccessCode
                handler?.apply {
                    try {
                        val accessCode = getAccessCode()
                        if (accessCode != null) {
                            withContext(Dispatchers.Main) {
                                _cardAccessCode.value = accessCode
                            }
                        } else {
                            Log.w(TAG, "Failed to get Access Code from handler")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing card", e)
                    }
                } ?: run {
                    Log.w(TAG, "Unsupported tag type: $techList")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Critical error in onTagDiscovered", e)
            }
        }
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