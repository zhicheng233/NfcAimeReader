package org.ohdj.nfcaimereader.ui.screen.setting

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.ohdj.nfcaimereader.data.datastore.FelicaPreferenceViewModel
import org.ohdj.nfcaimereader.ui.screen.setting.component.SettingSwitchItem

@Composable
fun FelicaSettingSection(viewModel: FelicaPreferenceViewModel) {
    val state = viewModel.felicaState.collectAsState().value

    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "读卡设置",
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        SettingSwitchItem(
            title = "FeliCa 兼容模式",
            description = "适用于非 Amusement IC FeliCa 及需兼容的用户，直接返回卡片 IDm",
            checked = state.felicaCompatibilityMode,
            onCheckedChange = { viewModel.updateFelicaCompatibility(it) }
        )
    }
}
