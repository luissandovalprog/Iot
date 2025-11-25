package com.example.evaluacion

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.firebase.database.FirebaseDatabase
import java.util.UUID

class DeviceControlActivity : AppCompatActivity() {

    // UUIDs (Tus mismos UUIDs)
    private val SERVICE_UUID = UUID.fromString("12345678-90ab-cdef-1234-567890abcdef")
    private val CONTROL_CHAR_UUID = UUID.fromString("abcdef02-1234-5678-90ab-cdef12345678")

    private var bluetoothGatt: BluetoothGatt? = null
    private var controlCharacteristic: BluetoothGattCharacteristic? = null
    private lateinit var tvStatus: TextView
    private var isConnected = false

    // Base de datos Firebase
    private val database = FirebaseDatabase.getInstance().reference

    // ID para notificaciones
    private val CHANNEL_ID = "canal_lluvia"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_control)

        setupUI()
        crearCanalNotificacion() // Configuramos las notificaciones

        // Lanzamos notificación de Bienvenida al entrar
        lanzarNotificacion("App Lluvia", "¡Bienvenido al sistema de control!")

        val deviceAddress = intent.getStringExtra("DEVICE_ADDRESS")
        if (deviceAddress != null) {
            connectToBLEDevice(deviceAddress)
        }
    }

    private fun setupUI() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar_control)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = intent.getStringExtra("DEVICE_NAME") ?: "Control BLE"

        tvStatus = findViewById(R.id.tv_status)

        findViewById<Button>(R.id.btn_open_window).setOnClickListener {
            sendBLECommand("b")
            database.child("estado_ventana").setValue("ABIERTA")
        }

        findViewById<Button>(R.id.btn_close_window).setOnClickListener {
            sendBLECommand("a")
            database.child("estado_ventana").setValue("CERRADA")
        }
    }

    // --- FUNCIONES DE NOTIFICACIÓN ---
    private fun crearCanalNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Alertas Lluvia"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance)
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun lanzarNotificacion(titulo: String, mensaje: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
                return
            }
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(titulo)
            .setContentText(mensaje)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    // --- LÓGICA DE SIMULACIÓN DE LLUVIA ---
    // Como el Bluetooth no está leyendo datos reales todavía, puedes llamar a
    // lanzarNotificacion("ALERTA", "¡Se detectó lluvia!") cuando quieras probarlo.

    // --- LÓGICA BLUETOOTH (Igual que tenías) ---
    private fun connectToBLEDevice(address: String) {
        tvStatus.text = "Estado: Conectando a BLE..."
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        val device = adapter.getRemoteDevice(address)
        bluetoothGatt = device.connectGatt(this, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                isConnected = true
                runOnUiThread { tvStatus.text = "Estado: Conectado" }
                if (ActivityCompat.checkSelfPermission(this@DeviceControlActivity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    gatt?.discoverServices()
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                isConnected = false
                runOnUiThread { tvStatus.text = "Estado: Desconectado" }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt?.getService(SERVICE_UUID)
                controlCharacteristic = service?.getCharacteristic(CONTROL_CHAR_UUID)
                if (controlCharacteristic != null) {
                    runOnUiThread {
                        tvStatus.text = "Estado: Listo"
                        Toast.makeText(this@DeviceControlActivity, "Conectado a Arduino", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun sendBLECommand(command: String) {
        if (bluetoothGatt == null || controlCharacteristic == null) return
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return

        controlCharacteristic?.setValue(command)
        controlCharacteristic?.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        val success = bluetoothGatt?.writeCharacteristic(controlCharacteristic) ?: false

        if (success) {
            val accion = if(command == "a") "Cerrar" else "Abrir"
            tvStatus.text = "Comando enviado: $accion"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            bluetoothGatt?.close()
        }
        bluetoothGatt = null
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}