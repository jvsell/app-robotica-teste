package com.example.robohc06;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.BluetoothProfile;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final int COLOR_BACKGROUND = Color.rgb(242, 245, 249);
    private static final int COLOR_SURFACE = Color.WHITE;
    private static final int COLOR_TEXT = Color.rgb(15, 23, 42);
    private static final int COLOR_MUTED = Color.rgb(71, 85, 105);
    private static final int COLOR_PRIMARY = Color.rgb(20, 184, 166);
    private static final int COLOR_PRIMARY_DARK = Color.rgb(15, 118, 110);
    private static final int COLOR_DANGER = Color.rgb(239, 68, 68);
    private static final int REQUEST_BLUETOOTH_CONNECT = 10;
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final UUID BLE_UART_SERVICE_UUID = UUID.fromString("0000FFE0-0000-1000-8000-00805F9B34FB");
    private static final UUID BLE_UART_CHARACTERISTIC_UUID = UUID.fromString("0000FFE1-0000-1000-8000-00805F9B34FB");
    private static final UUID BLE_UART_WRITE_CHARACTERISTIC_UUID = UUID.fromString("0000FFE2-0000-1000-8000-00805F9B34FB");
    private static final UUID EMPTY_UUID = UUID.fromString("00000000-0000-0000-0000-000000000000");
    private static final long BLE_CONNECT_TIMEOUT_MS = 12000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<BluetoothDevice> pairedDevices = new ArrayList<>();
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothDevice selectedDevice;
    private BluetoothSocket socket;
    private OutputStream outputStream;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic bleWriteCharacteristic;
    private boolean bleConnected = false;
    private boolean bleWriteInProgress = false;
    private String pendingBleCommand = null;
    private TextView statusText;
    private TextView logText;
    private final StringBuilder connectionLog = new StringBuilder();
    private Button connectButton;
    private JoystickView joystickView;
    private boolean connectingBle = false;

    private final Runnable commandLoop = new Runnable() {
        @Override
        public void run() {
            sendJoystickCommand();
            handler.postDelayed(this, bleConnected ? 300 : 80);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        buildLayout();
        requestBluetoothPermissionIfNeeded();
        loadPairedDevices();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(commandLoop);
        disconnect();
        super.onDestroy();
    }

    private void buildLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 28, 28, 28);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(COLOR_BACKGROUND);

        TextView title = new TextView(this);
        title.setText("Robo HC-06");
        title.setTextSize(30);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(COLOR_TEXT);
        title.setGravity(Gravity.CENTER);

        statusText = new TextView(this);
        statusText.setText("Pareie o HC-06 no Android e conecte aqui.");
        statusText.setTextSize(16);
        statusText.setTextColor(COLOR_PRIMARY_DARK);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(22, 14, 22, 14);
        statusText.setBackground(createRoundedBackground(Color.rgb(204, 251, 241), 28, 0));

        logText = new TextView(this);
        logText.setText("Log: aguardando conexao.");
        logText.setTextSize(13);
        logText.setTextColor(COLOR_MUTED);
        logText.setPadding(22, 18, 22, 18);
        logText.setMaxLines(10);
        logText.setBackground(createRoundedBackground(COLOR_SURFACE, 18, Color.rgb(226, 232, 240)));

        Spinner deviceSpinner = new Spinner(this);
        deviceSpinner.setPadding(12, 8, 12, 8);
        deviceSpinner.setBackground(createRoundedBackground(COLOR_SURFACE, 14, Color.rgb(203, 213, 225)));
        deviceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedDevice = pairedDevices.isEmpty() ? null : pairedDevices.get(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedDevice = null;
            }
        });

        connectButton = new Button(this);
        connectButton.setText("Conectar");
        styleButton(connectButton, COLOR_PRIMARY_DARK);
        connectButton.setOnClickListener(v -> {
            if (isConnected()) {
                disconnect();
            } else {
                connect();
            }
        });

        Button stopButton = new Button(this);
        stopButton.setText("Parar");
        styleButton(stopButton, COLOR_DANGER);
        stopButton.setOnClickListener(v -> {
            joystickView.center();
            sendRawCommand("F0T0D0E0\n");
        });

        joystickView = new JoystickView(this);

        LinearLayout.LayoutParams matchWrap = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        matchWrap.setMargins(0, 12, 0, 12);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1
        );
        buttonParams.setMargins(6, 0, 6, 0);
        buttons.addView(connectButton, buttonParams);
        buttons.addView(stopButton, buttonParams);

        root.addView(title, matchWrap);
        root.addView(statusText, matchWrap);
        root.addView(deviceSpinner, matchWrap);
        root.addView(buttons, matchWrap);
        root.addView(logText, matchWrap);
        root.addView(joystickView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        setContentView(root);
        deviceSpinner.setTag("deviceSpinner");
    }

    private Spinner getDeviceSpinner() {
        return (Spinner) ((View) statusText.getParent()).findViewWithTag("deviceSpinner");
    }

    private GradientDrawable createRoundedBackground(int color, int radius, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeColor != 0) {
            drawable.setStroke(2, strokeColor);
        }
        return drawable;
    }

    private void styleButton(Button button, int color) {
        button.setAllCaps(false);
        button.setTextSize(16);
        button.setTextColor(Color.WHITE);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setPadding(18, 12, 18, 12);
        button.setBackground(createRoundedBackground(color, 14, 0));
    }

    private void requestBluetoothPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && (!hasBluetoothConnectPermission() || !hasBluetoothScanPermission())) {
            requestPermissions(new String[]{
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
            }, REQUEST_BLUETOOTH_CONNECT);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH_CONNECT) {
            loadPairedDevices();
        }
    }

    private void loadPairedDevices() {
        if (bluetoothAdapter == null) {
            statusText.setText("Bluetooth nao disponivel neste aparelho.");
            return;
        }
        if (!hasBluetoothConnectPermission()) {
            statusText.setText("Permissao de Bluetooth pendente.");
            return;
        }

        pairedDevices.clear();
        List<String> labels = new ArrayList<>();
        Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();
        for (BluetoothDevice device : bondedDevices) {
            pairedDevices.add(device);
            String name = device.getName() == null ? "Sem nome" : device.getName();
            labels.add(name + " - " + device.getAddress());
        }

        if (labels.isEmpty()) {
            labels.add("Nenhum dispositivo pareado");
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                labels
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        getDeviceSpinner().setAdapter(adapter);
        selectedDevice = pairedDevices.isEmpty() ? null : pairedDevices.get(0);
    }

    private void connect() {
        if (selectedDevice == null) {
            showToast("Pareie o HC-06 nas configuracoes do Android primeiro.");
            return;
        }
        if (!hasBluetoothConnectPermission()) {
            requestBluetoothPermissionIfNeeded();
            return;
        }

        statusText.setText("Conectando...");
        resetConnectionLog("Tentando conectar em " + selectedDevice.getAddress());
        connectButton.setEnabled(false);

        connectBleUart(selectedDevice);
    }

    private void connectBleUart(BluetoothDevice device) {
        appendConnectionLog("Tentando BLE UART FFE0/FFE1 primeiro.");
        connectingBle = true;
        handler.postDelayed(() -> {
            if (connectingBle && !bleConnected) {
                appendConnectionLog("BLE demorou demais. Tentando RFCOMM.");
                closeGattOnly();
                connectRfcommFallback();
            }
        }, BLE_CONNECT_TIMEOUT_MS);
        try {
            bluetoothGatt = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    ? device.connectGatt(this, false, bleGattCallback, BluetoothDevice.TRANSPORT_LE)
                    : device.connectGatt(this, false, bleGattCallback);
        } catch (SecurityException error) {
            appendConnectionLog("BLE falhou por permissao: " + shortError(error));
            connectingBle = false;
            connectRfcommFallback();
        }
    }

    private final BluetoothGattCallback bleGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                appendConnectionLog("BLE status falhou: " + status + ". Tentando RFCOMM.");
                connectingBle = false;
                closeGattOnly();
                connectRfcommFallback();
                return;
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                appendConnectionLog("BLE conectado. Descobrindo servicos.");
                try {
                    gatt.discoverServices();
                } catch (SecurityException error) {
                    appendConnectionLog("BLE discover falhou: " + shortError(error));
                    connectingBle = false;
                    closeGattOnly();
                    connectRfcommFallback();
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                appendConnectionLog("BLE desconectado.");
                closeGattOnly();
                handler.post(() -> {
                    connectButton.setText("Conectar");
                    connectButton.setEnabled(true);
                    statusText.setText("Desconectado.");
                });
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                appendConnectionLog("BLE servicos falharam: " + status + ". Tentando RFCOMM.");
                connectingBle = false;
                closeGattOnly();
                connectRfcommFallback();
                return;
            }

            BluetoothGattCharacteristic characteristic = findBleWriteCharacteristic(gatt);
            if (characteristic == null) {
                appendConnectionLog("BLE FFE1 nao encontrado. Tentando RFCOMM.");
                connectingBle = false;
                closeGattOnly();
                connectRfcommFallback();
                return;
            }

            bleWriteCharacteristic = characteristic;
            bleWriteCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            bleConnected = true;
            connectingBle = false;
            handler.post(() -> {
                statusText.setText("Conectado BLE: " + selectedDevice.getName());
                appendConnectionLog("BLE UART pronto para escrita.");
                connectButton.setText("Desconectar");
                connectButton.setEnabled(true);
                handler.removeCallbacks(commandLoop);
                handler.post(commandLoop);
            });
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            bleWriteInProgress = false;
            if (status != BluetoothGatt.GATT_SUCCESS) {
                appendConnectionLog("BLE write callback falhou: " + status);
            }
            String nextCommand = pendingBleCommand;
            pendingBleCommand = null;
            if (nextCommand != null && bleConnected) {
                writeBleRaw(nextCommand);
            }
        }
    };

    private BluetoothGattCharacteristic findBleWriteCharacteristic(BluetoothGatt gatt) {
        BluetoothGattService uartService = gatt.getService(BLE_UART_SERVICE_UUID);
        if (uartService != null) {
            BluetoothGattCharacteristic writeCharacteristic = uartService.getCharacteristic(BLE_UART_WRITE_CHARACTERISTIC_UUID);
            if (isWritable(writeCharacteristic)) {
                appendConnectionLog("BLE usando FFE2 para escrita.");
                return writeCharacteristic;
            }
            BluetoothGattCharacteristic uartCharacteristic = uartService.getCharacteristic(BLE_UART_CHARACTERISTIC_UUID);
            if (isWritable(uartCharacteristic)) {
                appendConnectionLog("BLE usando FFE1 para escrita.");
                return uartCharacteristic;
            }
        }

        for (BluetoothGattService service : gatt.getServices()) {
            for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                if (isWritable(characteristic)) {
                    appendConnectionLog("BLE usando caracteristica gravavel " + shortUuid(characteristic.getUuid()) + ".");
                    return characteristic;
                }
            }
        }
        return null;
    }

    private boolean isWritable(BluetoothGattCharacteristic characteristic) {
        if (characteristic == null) {
            return false;
        }
        int properties = characteristic.getProperties();
        return (properties & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
                || (properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0;
    }

    private void connectRfcommFallback() {
        new Thread(() -> {
            try {
                cancelDiscoveryIfAllowed();
                BluetoothSocket nextSocket = openSerialSocket(selectedDevice);
                socket = nextSocket;
                outputStream = nextSocket.getOutputStream();
                handler.post(() -> {
                    statusText.setText("Conectado RFCOMM: " + selectedDevice.getName());
                    appendConnectionLog("Conexao RFCOMM aberta.");
                    connectButton.setText("Desconectar");
                    connectButton.setEnabled(true);
                    handler.removeCallbacks(commandLoop);
                    handler.post(commandLoop);
                });
            } catch (IOException error) {
                closeConnection();
                String errorMessage = error.getMessage() == null ? "Erro Bluetooth desconhecido." : error.getMessage();
                handler.post(() -> {
                    statusText.setText("Falha ao conectar: " + errorMessage);
                    connectButton.setText("Conectar");
                    connectButton.setEnabled(true);
                    showToast(errorMessage);
                });
            }
        }).start();
    }

    private BluetoothSocket openSerialSocket(BluetoothDevice device) throws IOException {
        IOException lastError = null;
        List<UUID> uuids = getCandidateUuids(device);

        int attempt = 1;
        for (UUID uuid : uuids) {
            BluetoothSocket insecureSocket = null;
            try {
                appendConnectionLog("Tentativa " + attempt + ": SPP inseguro " + shortUuid(uuid) + ".");
                insecureSocket = device.createInsecureRfcommSocketToServiceRecord(uuid);
                insecureSocket.connect();
                return insecureSocket;
            } catch (IOException error) {
                lastError = error;
                closeQuietly(insecureSocket);
                appendConnectionLog("SPP inseguro falhou: " + shortError(error));
                sleepBeforeRetry();
            }
            attempt++;
        }

        for (UUID uuid : uuids) {
            BluetoothSocket secureSocket = null;
            try {
                appendConnectionLog("Tentativa " + attempt + ": SPP seguro " + shortUuid(uuid) + ".");
                secureSocket = device.createRfcommSocketToServiceRecord(uuid);
                secureSocket.connect();
                return secureSocket;
            } catch (IOException error) {
                lastError = error;
                closeQuietly(secureSocket);
                appendConnectionLog("SPP seguro falhou: " + shortError(error));
                sleepBeforeRetry();
            }
            attempt++;
        }

        for (int channel = 1; channel <= 10; channel++) {
            BluetoothSocket channelSocket = null;
            try {
                appendConnectionLog("Tentativa " + attempt + ": canal RFCOMM " + channel + ".");
                Method method = device.getClass().getMethod("createRfcommSocket", int.class);
                channelSocket = (BluetoothSocket) method.invoke(device, channel);
                channelSocket.connect();
                return channelSocket;
            } catch (Exception error) {
                closeQuietly(channelSocket);
                lastError = error instanceof IOException ? (IOException) error : new IOException(error);
                appendConnectionLog("Canal " + channel + " falhou: " + shortError(error));
                sleepBeforeRetry();
            }
            attempt++;
        }

        for (int channel = 1; channel <= 10; channel++) {
            BluetoothSocket channelSocket = null;
            try {
                appendConnectionLog("Tentativa " + attempt + ": canal inseguro " + channel + ".");
                Method method = device.getClass().getMethod("createInsecureRfcommSocket", int.class);
                channelSocket = (BluetoothSocket) method.invoke(device, channel);
                channelSocket.connect();
                return channelSocket;
            } catch (Exception error) {
                closeQuietly(channelSocket);
                lastError = error instanceof IOException ? (IOException) error : new IOException(error);
                appendConnectionLog("Canal inseguro " + channel + " falhou: " + shortError(error));
                sleepBeforeRetry();
            }
            attempt++;
        }

        throw lastError == null ? new IOException("Nenhuma tentativa abriu o socket.") : lastError;
    }

    private void cancelDiscoveryIfAllowed() {
        if (bluetoothAdapter == null) {
            return;
        }
        try {
            if (bluetoothAdapter.isDiscovering()) {
                appendConnectionLog("Cancelando busca Bluetooth ativa.");
                bluetoothAdapter.cancelDiscovery();
            }
        } catch (SecurityException error) {
            appendConnectionLog("Nao foi possivel cancelar busca: " + shortError(error));
        }
    }

    private List<UUID> getCandidateUuids(BluetoothDevice device) {
        LinkedHashSet<UUID> uuidSet = new LinkedHashSet<>();
        uuidSet.add(SPP_UUID);
        if (hasBluetoothConnectPermission() && device.getUuids() != null) {
            for (android.os.ParcelUuid parcelUuid : device.getUuids()) {
                UUID uuid = parcelUuid.getUuid();
                if (!EMPTY_UUID.equals(uuid)) {
                    uuidSet.add(uuid);
                }
            }
        }
        appendConnectionLog("UUIDs candidatos: " + uuidSet.size() + ".");
        return new ArrayList<>(uuidSet);
    }

    private void disconnect() {
        handler.removeCallbacks(commandLoop);
        closeConnection();
        if (connectButton != null) {
            connectButton.setText("Conectar");
            connectButton.setEnabled(true);
        }
        if (statusText != null) {
            statusText.setText("Desconectado.");
        }
        setConnectionLog("Desconectado.");
    }

    private void closeConnection() {
        try {
            if (outputStream != null) {
                outputStream.write("F0T0D0E0\n".getBytes(StandardCharsets.US_ASCII));
                outputStream.flush();
            }
        } catch (IOException ignored) {
        }
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
        outputStream = null;
        socket = null;
        if (bleConnected) {
            writeBleRaw("F0T0D0E0\n");
        }
        closeGattOnly();
    }

    private void closeGattOnly() {
        connectingBle = false;
        bleConnected = false;
        bleWriteInProgress = false;
        pendingBleCommand = null;
        bleWriteCharacteristic = null;
        if (bluetoothGatt != null) {
            try {
                bluetoothGatt.close();
            } catch (SecurityException ignored) {
            }
        }
        bluetoothGatt = null;
    }

    private void closeQuietly(BluetoothSocket bluetoothSocket) {
        if (bluetoothSocket == null) {
            return;
        }
        try {
            bluetoothSocket.close();
        } catch (IOException ignored) {
        }
    }

    private boolean isConnected() {
        return (socket != null && socket.isConnected() && outputStream != null)
                || (bleConnected && bluetoothGatt != null && bleWriteCharacteristic != null);
    }

    private void sendJoystickCommand() {
        int forward = joystickView.getForward();
        int backward = joystickView.getBackward();
        int right = joystickView.getRightCommand();
        int left = joystickView.getLeftCommand();
        sendRawCommand("F" + forward + "T" + backward + "D" + right + "E" + left + "\n");
    }

    private void sendRawCommand(String command) {
        if (!isConnected()) {
            return;
        }
        if (bleConnected && bluetoothGatt != null && bleWriteCharacteristic != null) {
            writeBleRaw(command);
            return;
        }
        try {
            outputStream.write(command.getBytes(StandardCharsets.US_ASCII));
            outputStream.flush();
        } catch (IOException error) {
            disconnect();
        }
    }

    private void writeBleRaw(String command) {
        if (bluetoothGatt == null || bleWriteCharacteristic == null) {
            return;
        }
        if (bleWriteInProgress) {
            pendingBleCommand = command;
            return;
        }
        byte[] payload = command.getBytes(StandardCharsets.US_ASCII);
        try {
            bleWriteInProgress = true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                int result = bluetoothGatt.writeCharacteristic(
                        bleWriteCharacteristic,
                        payload,
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                );
                if (result != BluetoothGatt.GATT_SUCCESS) {
                    bleWriteInProgress = false;
                    appendConnectionLog("BLE write iniciou com falha: " + result);
                }
            } else {
                bleWriteCharacteristic.setValue(payload);
                bleWriteCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                if (!bluetoothGatt.writeCharacteristic(bleWriteCharacteristic)) {
                    bleWriteInProgress = false;
                    appendConnectionLog("BLE write iniciou com falha.");
                }
            }
        } catch (SecurityException error) {
            bleWriteInProgress = false;
            appendConnectionLog("BLE write sem permissao: " + shortError(error));
            disconnect();
        }
    }

    private boolean hasBluetoothConnectPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasBluetoothScanPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
    }

    private void showToast(String message) {
        if (message == null || message.trim().isEmpty()) {
            return;
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void setConnectionLog(String message) {
        handler.post(() -> {
            if (logText != null) {
                logText.setText("Log: " + message);
            }
        });
    }

    private void resetConnectionLog(String message) {
        connectionLog.setLength(0);
        appendConnectionLog(message);
    }

    private void appendConnectionLog(String message) {
        if (connectionLog.length() > 0) {
            connectionLog.append('\n');
        }
        connectionLog.append(message);
        setConnectionLog(connectionLog.toString());
    }

    private String shortUuid(UUID uuid) {
        String value = uuid.toString();
        return value.length() <= 8 ? value : value.substring(0, 8);
    }

    private String shortError(Exception error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return error.getClass().getSimpleName();
        }
        return message;
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(700);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }

    public static class JoystickView extends View {
        private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint knobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float x;
        private float y;

        public JoystickView(Activity context) {
            super(context);
            basePaint.setColor(Color.rgb(226, 232, 240));
            knobPaint.setColor(COLOR_PRIMARY_DARK);
            axisPaint.setColor(Color.rgb(148, 163, 184));
            axisPaint.setStrokeWidth(3f);
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeWidth(8f);
            ringPaint.setColor(COLOR_PRIMARY);
            labelPaint.setColor(COLOR_MUTED);
            labelPaint.setTextAlign(Paint.Align.CENTER);
            labelPaint.setTextSize(28f);
            labelPaint.setTypeface(Typeface.DEFAULT_BOLD);
        }

        @Override
        protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            center();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float centerX = getWidth() / 2f;
            float centerY = getHeight() / 2f;
            float radius = Math.min(getWidth(), getHeight()) * 0.36f;
            float knobRadius = radius * 0.28f;

            canvas.drawCircle(centerX, centerY, radius, basePaint);
            canvas.drawCircle(centerX, centerY, radius + 8f, ringPaint);
            canvas.drawLine(centerX - radius, centerY, centerX + radius, centerY, axisPaint);
            canvas.drawLine(centerX, centerY - radius, centerX, centerY + radius, axisPaint);
            canvas.drawText("F", centerX, centerY - radius - 28f, labelPaint);
            canvas.drawText("T", centerX, centerY + radius + 48f, labelPaint);
            canvas.drawText("E", centerX - radius - 36f, centerY + 10f, labelPaint);
            canvas.drawText("D", centerX + radius + 36f, centerY + 10f, labelPaint);
            canvas.drawCircle(x, y, knobRadius, knobPaint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                center();
                return true;
            }

            float centerX = getWidth() / 2f;
            float centerY = getHeight() / 2f;
            float radius = Math.min(getWidth(), getHeight()) * 0.36f;
            float dx = event.getX() - centerX;
            float dy = event.getY() - centerY;
            float distance = (float) Math.sqrt(dx * dx + dy * dy);

            if (distance > radius) {
                dx = dx / distance * radius;
                dy = dy / distance * radius;
            }

            x = centerX + dx;
            y = centerY + dy;
            invalidate();
            return true;
        }

        public void center() {
            x = getWidth() / 2f;
            y = getHeight() / 2f;
            invalidate();
        }

        public int getForward() {
            return scaleAxis(-(y - getHeight() / 2f));
        }

        public int getBackward() {
            return scaleAxis(y - getHeight() / 2f);
        }

        public int getRightCommand() {
            return scaleAxis(x - getWidth() / 2f);
        }

        public int getLeftCommand() {
            return scaleAxis(-(x - getWidth() / 2f));
        }

        private int scaleAxis(float value) {
            float radius = Math.min(getWidth(), getHeight()) * 0.36f;
            if (radius <= 0) {
                return 0;
            }
            int scaled = Math.round(Math.max(0f, value) / radius * 255f);
            return Math.min(255, Math.max(0, scaled));
        }
    }
}
