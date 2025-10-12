package org.ohdj.nfcaimereader.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.ohdj.nfcaimereader.data.repository.WebSocketRepository
import org.ohdj.nfcaimereader.utils.NfcManager
import org.ohdj.nfcaimereader.utils.NfcState
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    val nfcManager: NfcManager,
    private val webSocketRepository: WebSocketRepository
) : ViewModel() {

    // 向UI公开NFC状态和卡片ID
    val nfcState: StateFlow<NfcState> = nfcManager.nfcState
    val cardAccessCode: StateFlow<String?> = nfcManager.cardAccessCode

    init {
        viewModelScope.launch {
            nfcManager.cardAccessCode
                .filterNotNull() // 仅在有实际卡片ID可用时处理
                .collect { accessCode ->
                    if (webSocketRepository.connectionState.value.isConnected) {
                        webSocketRepository.sendCardId(accessCode)
                    }
                }
        }
    }
}