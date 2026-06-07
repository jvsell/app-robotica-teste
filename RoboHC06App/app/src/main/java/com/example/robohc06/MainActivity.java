package com.example.robohc06;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
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
    private static final int REQUEST_BLUETOOTH_CONNECT = 10;
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final UUID EMPTY_UUID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<BluetoothDevice> pairedDevices = new ArrayList<>();
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothDevice selectedDevice;
    private BluetoothSocket socket;
    private OutputStream outputStream;
    private TextView statusText;
    private TextView logText;
    private final StringBuilder connectionLog = new StringBuilder();
    private Button connectButton;
    private JoystickView joystickView;

    private final Runnable commandLoop = new Runnable() {
        @Override
        public void run() {
            sendJoystickCommand();
            handler.postDelayed(this, 80);
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
        root.setPadding(32, 32, 32, 32);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Color.rgb(245, 247, 250));

        TextView title = new TextView(this);
        title.setText("Robo HC-06");
        title.setTextSize(28);
        title.setTextColor(Color.rgb(17, 24, 39));
        title.setGravity(Gravity.CENTER);

        statusText = new TextView(this);
        statusText.setText("Pareie o HC-06 no Android e conecte aqui.");
        statusText.setTextSize(16);
        statusText.setTextColor(Color.rgb(75, 85, 99));
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, 16, 0, 16);

        logText = new TextView(this);
        logText.setText("Log: aguardando conexao.");
        logText.setTextSize(13);
        logText.setTextColor(Color.rgb(55, 65, 81));
        logText.setPadding(0, 8, 0, 8);
        logText.setMaxLines(10);

        Spinner deviceSpinner = new Spinner(this);
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
        connectButton.setOnClickListener(v -> {
            if (isConnected()) {
                disconnect();
            } else {
                connect();
            }
        });

        Button stopButton = new Button(this);
        stopButton.setText("Parar");
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

        root.addView(title, matchWrap);
        root.addView(statusText, matchWrap);
        root.addView(logText, matchWrap);
        root.addView(deviceSpinner, matchWrap);
        root.addView(connectButton, matchWrap);
        root.addView(stopButton, matchWrap);
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

        new Thread(() -> {
            try {
                cancelDiscoveryIfAllowed();
                BluetoothSocket nextSocket = openSerialSocket(selectedDevice);
                socket = nextSocket;
                outputStream = nextSocket.getOutputStream();
                handler.post(() -> {
                    statusText.setText("Conectado: " + selectedDevice.getName());
                    setConnectionLog("Conexao aberta. Enviando comandos F/T/D/E.");
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
        return socket != null && socket.isConnected() && outputStream != null;
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
        try {
            outputStream.write(command.getBytes(StandardCharsets.US_ASCII));
            outputStream.flush();
        } catch (IOException error) {
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
        private float x;
        private float y;

        public JoystickView(Activity context) {
            super(context);
            basePaint.setColor(Color.rgb(209, 213, 219));
            knobPaint.setColor(Color.rgb(15, 118, 110));
            axisPaint.setColor(Color.rgb(107, 114, 128));
            axisPaint.setStrokeWidth(4f);
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
            canvas.drawLine(centerX - radius, centerY, centerX + radius, centerY, axisPaint);
            canvas.drawLine(centerX, centerY - radius, centerX, centerY + radius, axisPaint);
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
