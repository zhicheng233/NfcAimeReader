package com.example.nfcaimereader;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.nfcaimereader.Client.SpiceClient;
import com.example.nfcaimereader.Services.NfcEventListener;
import com.example.nfcaimereader.Services.NfcHandler;
import com.example.nfcaimereader.Services.NfcHelper;
import com.example.nfcaimereader.Services.NfcStateReceiver;
import com.example.nfcaimereader.databinding.ActivityMainBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class MainActivity extends AppCompatActivity implements NfcStateReceiver.NfcStateChangeListener, SpiceClient.ConnectionStatusCallback, NfcEventListener {
    // NFC状态变化
    private NfcStateReceiver nfcStateReceiver;

    // NFC初始化
    private NfcHelper nfcHelper;
    private NfcHandler nfcHandler;

    // viewBinding
    private ActivityMainBinding binding;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(this);
        if (adapter != null && adapter.isEnabled()) {
            binding.progressbarNfcDelay.setVisibility(View.GONE);
            binding.buttonNfcSetting.setVisibility(View.GONE);
            binding.textviewNfcStatus.setText("NFC已开启");
        } else if (adapter != null && !adapter.isEnabled()) {
            binding.progressbarNfcDelay.setVisibility(View.GONE);
            binding.buttonNfcSetting.setVisibility(View.VISIBLE);
            binding.textviewNfcStatus.setText("NFC已关闭");
        }

        // 初始化BroadcastReceiver
        nfcStateReceiver = new NfcStateReceiver(this);
        // 注册BroadcastReceiver
        IntentFilter filter = new IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED);
        this.registerReceiver(nfcStateReceiver, filter);

        // NFC初始化
        nfcHelper = new NfcHelper(this);
        nfcHandler = new NfcHandler(this);

        // WebSocket回调
        SpiceClient.getInstance().setConnectionStatusCallback(this);

        // 设置NFC设置按钮的点击事件
        binding.buttonNfcSetting.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NFC_SETTINGS)));

        binding.buttonSettingServer.setOnClickListener(v -> handleConnectButtonClick());

        loadHostnameAndPort();
    }

    private void handleConnectButtonClick() {
        String buttonText = binding.buttonSettingServer.getText().toString();
        switch (buttonText) {
            case "设定服务器":
                showEditServerDialog(false);
                break;
            case "编辑":
                showEditServerDialog(true);
                break;
        }
    }

    private void loadHostnameAndPort() {
        SharedPreferences sharedPref = this.getSharedPreferences("AppSettings", Context.MODE_PRIVATE);
        String hostname = sharedPref.getString("hostname", "");
        String port = sharedPref.getString("port", "");

        // 判断 SharedPreferences 中是否有保存的值
        boolean hasSavedValues = !hostname.isEmpty() && !port.isEmpty();

        // 如果有保存的值，设置按钮文本为“编辑”，否则保持为“保存”
        if (hasSavedValues) {
            binding.buttonSettingServer.setText("编辑");
            binding.textviewServerAddress.setText(hostname + ":" + port);
            Toast.makeText(this, "已读取之前保存的服务器", Toast.LENGTH_SHORT).show();
        }
    }

    private void showEditServerDialog(boolean isEdit) {
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(isEdit ? "编辑服务器" : "设定服务器")
                .setView(R.layout.dialog_server_edit)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .setCancelable(false)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            Button saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            EditText editTextHostname = dialog.findViewById(R.id.edittext_hostname);
            EditText editTextPort = dialog.findViewById(R.id.edittext_port);

            // 如果是“编辑”状态，就加载存储的值
            if (isEdit) {
                SharedPreferences sharedPref = MainActivity.this.getSharedPreferences("AppSettings", Context.MODE_PRIVATE);
                String savedHostname = sharedPref.getString("hostname", "");
                String savedPort = sharedPref.getString("port", "");
                editTextHostname.setText(savedHostname);
                editTextPort.setText(savedPort);
            } else {
                // 如果是“设定服务器”状态，则禁用保存按钮
                saveButton.setEnabled(false);
            }

            // 创建TextWatcher来检查用户输入值是否符合保存服务器的条件
            TextWatcher watcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                /*
                    ^
                    (
                        0                   # 匹配0
                    |
                        (?!0)[0-9]{1,4}     # 匹配1到9999之间的任意数字且首位不能为0；其中{1,4}表示长度为1到4的数字串
                    |
                        [1-5][0-9]{4}       # 匹配10000到59999之间的数字；首位为1-5，其余为0-9的四位数
                    |
                        6[0-4][0-9]{3}      # 匹配60000到64999之间的数字；首位为6，第二位为0-4，后跟任意三位数字
                    |
                        65[0-4][0-9]{2}     # 匹配65000到65499之间的数字；前两位为65，第三位为0-4，后跟任意两位数字
                    |
                        655[0-2][0-9]       # 匹配65500到65529之间的数字；前三位为655，第四位为0-2，后跟任意一位数字
                    |
                        6553[0-5]           # 匹配65530到65535之间的数字；前四位为6553，最后一位为0-5之间的数字
                    )
                    $
                */
                    // 匹配从0到65535之间的数字，用于验证端口号
                    final String PORT_PATTERN = "^(0|(?!0)[0-9]{1,4}|[1-5][0-9]{4}|6[0-4][0-9]{3}|65[0-4][0-9]{2}|655[0-2][0-9]|6553[0-5])$";

                    String hostname = editTextHostname.getText().toString();
                    String portString = editTextPort.getText().toString();
                    boolean hostnameIsValid = !hostname.isEmpty();          // 确保hostname不为空
                    boolean portIsValid = portString.matches(PORT_PATTERN); // 检查端口号是否有效
                    saveButton.setEnabled(hostnameIsValid && portIsValid);  // 仅当hostname和端口号都有效时，才使保存按钮可点击
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            };

            // 给EditText添加监听
            editTextHostname.addTextChangedListener(watcher);
            editTextPort.addTextChangedListener(watcher);

            saveButton.setOnClickListener(view -> {
                String hostname = editTextHostname.getText().toString();
                String port = editTextPort.getText().toString();

                // 保存到SharedPreferences
                SharedPreferences sharedPref = MainActivity.this.getSharedPreferences("AppSettings", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putString("hostname", hostname);
                editor.putString("port", port);
                editor.apply();

                Toast.makeText(MainActivity.this, "已保存", Toast.LENGTH_SHORT).show();

                binding.buttonSettingServer.setText("编辑");
                binding.textviewServerAddress.setText(hostname + ":" + port);

                // 关闭对话框
                dialog.dismiss();
            });
        });

        dialog.show();
    }

    @Override
    public void onNfcStateChanged(int state) {
        // 处理NFC状态变化
        switch (state) {
            case NfcAdapter.STATE_OFF:
                // NFC已关闭
                binding.progressbarNfcDelay.setVisibility(View.GONE);
                binding.buttonNfcSetting.setVisibility(View.VISIBLE);
                binding.textviewNfcStatus.setText("NFC已关闭");
                break;
            case NfcAdapter.STATE_TURNING_OFF:
                // NFC正在关闭
                binding.progressbarNfcDelay.setVisibility(View.VISIBLE);
                binding.buttonNfcSetting.setVisibility(View.GONE);
                binding.textviewNfcStatus.setText("NFC正在关闭");
                break;
            case NfcAdapter.STATE_ON:
                // NFC已开启
                binding.progressbarNfcDelay.setVisibility(View.GONE);
                binding.buttonNfcSetting.setVisibility(View.GONE);
                binding.textviewNfcStatus.setText("NFC已开启");
                break;
            case NfcAdapter.STATE_TURNING_ON:
                // NFC正在开启
                binding.progressbarNfcDelay.setVisibility(View.VISIBLE);
                binding.buttonNfcSetting.setVisibility(View.GONE);
                binding.textviewNfcStatus.setText("NFC正在开启");
                break;
        }
    }

    @Override
    public void onConnectionStatusChanged(boolean isConnected) {
        runOnUiThread(() -> {
            binding.buttonConnectServer.setText(isConnected ? "断开连接" : "连接服务器");
            binding.buttonConnectServer.setIcon(ContextCompat.getDrawable(this, isConnected ? R.drawable.ic_connect_server_close : R.drawable.ic_connect_server));
        });
        runOnUiThread(() -> binding.textviewServerConnectionStatus.setText(isConnected ? "已连接" : "已断开"));
    }

    @Override
    public void onMessageReceived(final String message) {
        // 确保在主线程上运行
        runOnUiThread(() -> binding.textviewServerResponse.setText(message));
    }

    @Override
    public void onTagDiscovered(String cardType, String cardNumber) {
        // 显示卡片类型和卡号
        binding.textviewCardType.setText("卡片类型: " + cardType);
        binding.textviewCardNumber.setText("卡号: " + cardNumber);
        // SpiceClient.getInstance().sendCardId(cardNumber, String.valueOf(binding.edittextPassword.getText()));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(intent.getAction())) {
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            if (tag != null) {
                nfcHandler.processTag(tag);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        nfcHelper.enableNfcScan(this);
    }


    @Override
    protected void onPause() {
        super.onPause();
        nfcHelper.disableNfcScan(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 注销BroadcastReceiver
        this.unregisterReceiver(nfcStateReceiver);
        // 关闭WebSocket连接
        SpiceClient.getInstance().closeWebSocket();
    }
}
