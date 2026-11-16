package com.example.lucescondominioiot

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MainActivity : ComponentActivity() {

    // Pide el permiso de Bluetooth al usuario (obligatorio desde Android 12)
    private val pedirPermisoBluetooth =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            pedirPermisoBluetooth.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }

        setContent {
            // Puedes cambiar MaterialTheme por el tema de tu proyecto (ej: LucesCondominioTheme)
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // llamamos a nuestro composable principal
                    AppLucesCondominio()
                }
            }
        }
    }
}

// ==========================================
// COMPOSABLE PRINCIPAL: decide qué pantalla mostrar
// ==========================================
@Composable
fun AppLucesCondominio() {
    val context = LocalContext.current

    // Base de datos SQLite (se crea una sola vez gracias a remember)
    val baseDatos = remember { BaseDatos(context) }

    // Variable de estado: usuario que inició sesión (null = nadie ha iniciado sesión)
    var usuarioActual by rememberSaveable { mutableStateOf<String?>(null) }

    // Variable de estado: true = este teléfono funciona como Nodo IoT (2° dispositivo Android)
    var modoNodo by rememberSaveable { mutableStateOf(false) }

    val usuario = usuarioActual
    if (usuario == null) {
        // Control de acceso: sin login solo se ve esta pantalla
        PantallaLogin(
            baseDatos = baseDatos,
            onLoginCorrecto = { usuarioActual = it }
        )
    } else if (modoNodo) {
        PantallaNodo(
            baseDatos = baseDatos,
            onVolver = { modoNodo = false }
        )
    } else {
        PantallaPanel(
            baseDatos = baseDatos,
            operador = usuario,
            onSalir = { usuarioActual = null },
            onModoNodo = { modoNodo = true }
        )
    }
}

// ==========================================
// PANTALLA 1: LOGIN
// ==========================================
@Composable
fun PantallaLogin(baseDatos: BaseDatos, onLoginCorrecto: (String) -> Unit) {
    // Variables de estado: lo que escribe el usuario
    var usuario by remember { mutableStateOf("") }
    var clave by remember { mutableStateOf("") }

    // Variable de estado: mensaje para el usuario
    var mensaje by remember { mutableStateOf("Ingresa tus credenciales") }

    // Column organiza los elementos de forma vertical
    Column(
        modifier = Modifier
            .fillMaxSize()          // Ocupa toda la pantalla
            .safeDrawingPadding()   // No se tapa con la barra de estado
            .padding(24.dp),        // Margen interno
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 1. Título
        Text(
            text = "Alumbrado Condominio IoT",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = "Acceso seguro (SHA-256 + SQLite)",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 2. Campo de texto para el usuario
        TextField(
            value = usuario,
            onValueChange = { usuario = it },
            label = { Text("Usuario") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 3. Campo de texto para la contraseña (se muestra con puntos)
        TextField(
            value = clave,
            onValueChange = { clave = it },
            label = { Text("Contraseña") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 4. Botón para iniciar sesión
        Button(
            onClick = {
                val nombre = usuario.trim()
                if (baseDatos.validarUsuario(nombre, clave)) {
                    baseDatos.guardarEvento("Inicio de sesión ($nombre)")
                    onLoginCorrecto(nombre)
                } else {
                    mensaje = "Usuario o contraseña incorrectos"
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Iniciar sesión")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 5. Botón para registrar un nuevo operador
        OutlinedButton(
            onClick = {
                val nombre = usuario.trim()
                mensaje = when {
                    nombre.length < 3 || clave.length < 4 ->
                        "Usuario mín. 3 caracteres y contraseña mín. 4"
                    baseDatos.registrarUsuario(nombre, clave) -> "Operador \"$nombre\" registrado"
                    else -> "Ese usuario ya existe"
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Registrar nuevo operador")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 6. Texto que muestra el mensaje actualizado
        Text(text = mensaje)
    }
}

// ==========================================
// PANTALLA 2: PANEL DE CONTROL IOT
// ==========================================
@SuppressLint("MissingPermission")
@Composable
fun PantallaPanel(
    baseDatos: BaseDatos,
    operador: String,
    onSalir: () -> Unit,
    onModoNodo: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope() // Para tareas que tardan (Bluetooth) sin congelar la pantalla
    val bluetooth = remember { ConexionBluetooth(context) }

    // Variables de estado del panel
    var conectado by remember { mutableStateOf(false) }
    var estadoConexion by remember { mutableStateOf("Desconectado (modo simulación)") }
    var lucesEncendidas by remember { mutableStateOf<Boolean?>(null) } // null = según sensor / sin datos
    var modo by remember { mutableStateOf("AUTOMÁTICO (Sensor LDR)") }
    var menuAbierto by remember { mutableStateOf(false) }
    var dispositivoElegido by remember { mutableStateOf<BluetoothDevice?>(null) }
    val dispositivos = remember { mutableStateListOf<BluetoothDevice>() }
    val historial = remember { mutableStateListOf<String>().apply { addAll(baseDatos.obtenerHistorial()) } }

    // Guarda un evento en SQLite y refresca la lista en pantalla
    fun registrar(evento: String) {
        baseDatos.guardarEvento(evento)
        historial.clear()
        historial.addAll(baseDatos.obtenerHistorial())
    }

    // Busca los dispositivos Bluetooth ya vinculados en el teléfono (ej: HC-05)
    fun buscarDispositivos() {
        dispositivos.clear()
        dispositivos.addAll(bluetooth.dispositivosVinculados())
        if (dispositivos.isEmpty()) {
            estadoConexion = "Sin dispositivos (vincula el HC-05, activa el Bluetooth y da el permiso)"
        }
    }

    // Conecta con el HC-05 y luego se queda escuchando lo que envía el Arduino
    fun conectar(dispositivo: BluetoothDevice) {
        estadoConexion = "Conectando a ${dispositivo.name}..."
        scope.launch {
            val ok = withContext(Dispatchers.IO) { bluetooth.conectar(dispositivo) }
            if (!ok) {
                estadoConexion = "No se pudo conectar (modo simulación)"
                return@launch
            }
            conectado = true
            estadoConexion = "Conectado a ${dispositivo.name}"
            registrar("Conectado a ${dispositivo.name}")

            // Ciclo de lectura: cada línea que envía el Arduino con println()
            while (true) {
                val linea = withContext(Dispatchers.IO) { bluetooth.leerLinea() } ?: break
                if (linea.isBlank()) continue
                registrar("Arduino: $linea")
                val estado = interpretarTrama(linea)
                estado.luces?.let { lucesEncendidas = it }
                estado.modo?.let { modo = it }
            }

            // Si llegamos aquí sin haber pulsado "Desconectar", se perdió la conexión
            if (conectado) {
                conectado = false
                bluetooth.desconectar()
                estadoConexion = "Conexión perdida (modo simulación)"
            }
        }
    }

    fun desconectar() {
        conectado = false
        bluetooth.desconectar()
        estadoConexion = "Desconectado (modo simulación)"
    }

    // Envía 'A', '1' o '0' al Arduino. Sin conexión, simula la respuesta.
    fun enviarComando(comando: Char) {
        if (conectado) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { bluetooth.enviar(comando) }
                registrar(if (ok) "Comando '$comando' enviado" else "Error al enviar '$comando'")
            }
        } else {
            val estado = simularComando(comando)
            lucesEncendidas = estado.luces
            estado.modo?.let { modo = it }
            registrar("[Simulación] Comando '$comando' -> $modo")
        }
    }

    // Al salir de esta pantalla se cierra la conexión Bluetooth
    DisposableEffect(Unit) {
        buscarDispositivos()
        onDispose { bluetooth.desconectar() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Cabecera con el operador y botón para salir
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Operador: $operador", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    desconectar()
                    onModoNodo()
                }) {
                    Text("Modo nodo")
                }
                Button(
                    onClick = {
                        desconectar()
                        registrar("Cierre de sesión ($operador)")
                        onSalir()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Text("Salir")
                }
            }
        }

        // 2. Selección y conexión del módulo HC-05
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                Text(
                    text = dispositivoElegido?.let { "${it.name} (${it.address})" }
                        ?: "Toca aquí para elegir el HC-05",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !conectado) {
                            buscarDispositivos()
                            menuAbierto = true
                        }
                        .padding(12.dp)
                )
                DropdownMenu(expanded = menuAbierto, onDismissRequest = { menuAbierto = false }) {
                    dispositivos.forEach { dispositivo ->
                        DropdownMenuItem(
                            text = { Text("${dispositivo.name} (${dispositivo.address})") },
                            onClick = {
                                dispositivoElegido = dispositivo
                                menuAbierto = false
                            }
                        )
                    }
                }
            }
            Button(onClick = {
                val elegido = dispositivoElegido
                when {
                    conectado -> desconectar()
                    elegido == null -> estadoConexion = "Primero elige un dispositivo"
                    else -> conectar(elegido)
                }
            }) {
                Text(if (conectado) "Desconectar" else "Conectar")
            }
        }
        Text(
            text = "Bluetooth: $estadoConexion",
            color = if (conectado) Color(0xFF2E7D32) else Color(0xFFE65100)
        )

        // 3. Tarjetas con el estado actual
        TarjetaInfo(
            titulo = "Luces del condominio",
            contenido = when (lucesEncendidas) {
                true -> "ENCENDIDAS"
                false -> "APAGADAS"
                null -> "Según sensor / esperando datos"
            },
            colorFondo = if (lucesEncendidas == true) Color(0xFFFFF8E1) else Color(0xFFE8EAF6)
        )
        TarjetaInfo(titulo = "Modo de operación", contenido = modo)

        // 4. Botones de control (los mismos comandos que espera el Arduino)
        Button(onClick = { enviarComando('A') }, modifier = Modifier.fillMaxWidth()) {
            Text("Modo automático - sensor LDR ('A')")
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { enviarComando('1') }, modifier = Modifier.weight(1f)) {
                Text("Prender ('1')")
            }
            Button(onClick = { enviarComando('0') }, modifier = Modifier.weight(1f)) {
                Text("Apagar ('0')")
            }
        }

        // 5. Historial guardado en SQLite
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Historial (SQLite)", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = {
                baseDatos.limpiarHistorial()
                historial.clear()
            }) {
                Text("Limpiar")
            }
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f) // Ocupa el espacio que queda
        ) {
            items(historial) { evento ->
                Text(text = evento, style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
            }
        }
    }
}

// ==========================================
// PANTALLA 3: NODO IOT (segundo teléfono Android)
// Este teléfono hace el papel del Arduino + HC-05: recibe 'A', '1', '0'
// y responde con la misma telemetría que envía el Arduino.
// ==========================================
@Composable
fun PantallaNodo(baseDatos: BaseDatos, onVolver: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bluetooth = remember { ConexionBluetooth(context) }

    // Variables de estado del nodo
    var activo by remember { mutableStateOf(false) }      // esperando o conectado
    var conectado by remember { mutableStateOf(false) }   // el panel está conectado
    var estado by remember { mutableStateOf("Detenido") }
    var modoAuto by remember { mutableStateOf(true) }
    var oscuro by remember { mutableStateOf(false) }      // simula el sensor LDR tapado
    var lucesEncendidas by remember { mutableStateOf(false) }

    // Arma el mismo mensaje que imprime el Arduino
    fun trama(): String {
        val luces = if (lucesEncendidas) "ON" else "OFF"
        val modo = if (modoAuto) "AUTO" else "MANUAL $luces"
        return "[TELEMETRIA] Luces: $luces | Modo: $modo"
    }

    // Guarda el estado en SQLite y lo envía al panel si está conectado
    fun enviarEstado() {
        val linea = trama()
        baseDatos.guardarEvento("Nodo: $linea")
        if (conectado) {
            scope.launch(Dispatchers.IO) { bluetooth.enviarTexto(linea) }
        }
    }

    // Misma lógica que procesarComando() del Arduino
    fun aplicarComando(comando: Char) {
        when (comando) {
            'A' -> modoAuto = true
            '1' -> { modoAuto = false; lucesEncendidas = true }
            '0' -> { modoAuto = false; lucesEncendidas = false }
        }
        if (modoAuto) lucesEncendidas = oscuro
        enviarEstado()
    }

    fun iniciar() {
        activo = true
        estado = "Esperando que el panel se conecte..."
        scope.launch {
            val ok = withContext(Dispatchers.IO) { bluetooth.esperarConexion() }
            if (!ok) {
                if (activo) estado = "No se pudo iniciar (revisa Bluetooth y permiso)"
                activo = false
                return@launch
            }
            conectado = true
            estado = "Panel de control conectado"
            enviarEstado()

            // Ciclo de lectura: un carácter por comando
            while (true) {
                val leido = withContext(Dispatchers.IO) { bluetooth.leerCaracter() }
                if (leido < 0) break
                val comando = leido.toChar()
                if (comando == 'A' || comando == '1' || comando == '0') aplicarComando(comando)
            }

            conectado = false
            bluetooth.desconectar()
            if (activo) estado = "El panel se desconectó"
            activo = false
        }
    }

    fun detener() {
        activo = false
        conectado = false
        bluetooth.desconectar()
        estado = "Detenido"
    }

    // Al salir de esta pantalla se cierra la conexión
    DisposableEffect(Unit) {
        onDispose { bluetooth.desconectar() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Título
        Text(text = "Nodo IoT (tablero de luces)", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "Este teléfono reemplaza al Arduino + HC-05. En el otro teléfono, " +
                "vincula este equipo en Ajustes > Bluetooth y elígelo en \"Conectar\".",
            style = MaterialTheme.typography.bodyMedium
        )

        // 2. Iniciar / detener el nodo
        Button(
            onClick = { if (activo) detener() else iniciar() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (activo) "Detener nodo" else "Iniciar nodo")
        }
        Text(
            text = "Estado: $estado",
            color = if (conectado) Color(0xFF2E7D32) else Color(0xFFE65100)
        )

        // 3. Estado actual del nodo
        TarjetaInfo(
            titulo = "Luces del condominio",
            contenido = if (lucesEncendidas) "ENCENDIDAS" else "APAGADAS",
            colorFondo = if (lucesEncendidas) Color(0xFFFFF8E1) else Color(0xFFE8EAF6)
        )
        TarjetaInfo(
            titulo = "Modo de operación",
            contenido = if (modoAuto) "AUTOMÁTICO (Sensor LDR)" else "MANUAL"
        )

        // 4. Simulación del sensor LDR
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Sensor LDR tapado (está oscuro)")
            Switch(
                checked = oscuro,
                onCheckedChange = {
                    oscuro = it
                    if (modoAuto) {
                        lucesEncendidas = it
                        enviarEstado()
                    }
                }
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // 5. Volver al panel de control
        OutlinedButton(
            onClick = {
                detener()
                onVolver()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Volver al panel de control")
        }
    }
}

// Tarjeta reutilizable (igual que ExerciseCard de la práctica)
@Composable
fun TarjetaInfo(titulo: String, contenido: String, colorFondo: Color = Color(0xFFE8EAF6)) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = colorFondo)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = titulo, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = contenido, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

// ==========================================
// FUNCIONES Y CLASES DE APOYO
// ==========================================

// Estado que se obtiene de un mensaje del Arduino (null = no cambia)
data class EstadoArduino(val luces: Boolean?, val modo: String?)

// Lee mensajes como "[TELEMETRIA] Luces: ON | Modo: AUTO"
fun interpretarTrama(trama: String): EstadoArduino {
    val texto = trama.uppercase()
    val luces = when {
        "LUCES: ON" in texto || "MANUAL ON" in texto -> true
        "LUCES: OFF" in texto || "MANUAL OFF" in texto -> false
        else -> null
    }
    val modo = when {
        "MANUAL ON" in texto -> "MANUAL ON"
        "MANUAL OFF" in texto -> "MANUAL OFF"
        "MANUAL" in texto -> "MANUAL"
        "AUTO" in texto -> "AUTOMÁTICO (Sensor LDR)"
        else -> null
    }
    return EstadoArduino(luces, modo)
}

// Respuesta simulada cuando no hay HC-05 conectado (para el emulador o la demostración)
fun simularComando(comando: Char): EstadoArduino = when (comando) {
    'A' -> EstadoArduino(luces = null, modo = "AUTOMÁTICO (Sensor LDR)")
    '1' -> EstadoArduino(luces = true, modo = "MANUAL ON")
    '0' -> EstadoArduino(luces = false, modo = "MANUAL OFF")
    else -> EstadoArduino(luces = null, modo = null)
}

// Maneja la conexión Bluetooth clásica con el módulo HC-05
class ConexionBluetooth(private val context: Context) {

    // UUID estándar del perfil SPP (puerto serie por Bluetooth) que usa el HC-05
    private val uuidSPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val adaptador: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    @Volatile private var servidor: BluetoothServerSocket? = null
    @Volatile private var socket: BluetoothSocket? = null
    @Volatile private var lector: BufferedReader? = null

    private fun tienePermiso(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun dispositivosVinculados(): List<BluetoothDevice> {
        if (adaptador == null || !adaptador.isEnabled || !tienePermiso()) return emptyList()
        return adaptador.bondedDevices.toList()
    }

    // Tarda 1-3 segundos: se llama desde Dispatchers.IO
    @SuppressLint("MissingPermission")
    fun conectar(dispositivo: BluetoothDevice): Boolean {
        if (!tienePermiso()) return false
        return try {
            val nuevoSocket = dispositivo.createRfcommSocketToServiceRecord(uuidSPP)
            socket = nuevoSocket
            nuevoSocket.connect()
            lector = nuevoSocket.inputStream.bufferedReader()
            true
        } catch (e: IOException) {
            desconectar()
            false
        }
    }

    fun enviar(comando: Char): Boolean = try {
        val salida = socket?.outputStream
        if (salida == null) {
            false
        } else {
            salida.write(comando.code)
            salida.flush()
            true
        }
    } catch (e: IOException) {
        false
    }

    // MODO NODO: espera a que el panel de control (otro teléfono) se conecte
    @SuppressLint("MissingPermission")
    fun esperarConexion(): Boolean {
        if (adaptador == null || !adaptador.isEnabled || !tienePermiso()) return false
        return try {
            val nuevoServidor = adaptador.listenUsingRfcommWithServiceRecord("NodoLucesCondominio", uuidSPP)
            servidor = nuevoServidor
            val nuevoSocket = nuevoServidor.accept() // se queda esperando aquí
            nuevoServidor.close()
            servidor = null
            socket = nuevoSocket
            lector = nuevoSocket.inputStream.bufferedReader()
            true
        } catch (e: IOException) {
            desconectar()
            false
        }
    }

    // Envía una línea de texto (como println() del Arduino)
    fun enviarTexto(linea: String): Boolean = try {
        val salida = socket?.outputStream
        if (salida == null) {
            false
        } else {
            salida.write("$linea\n".toByteArray())
            salida.flush()
            true
        }
    } catch (e: IOException) {
        false
    }

    // Espera hasta recibir una línea. Devuelve null si la conexión se cerró.
    fun leerLinea(): String? = try {
        lector?.readLine()?.trim()
    } catch (e: IOException) {
        null
    }

    // Lee un solo carácter (comando). Devuelve -1 si la conexión se cerró.
    fun leerCaracter(): Int = try {
        lector?.read() ?: -1
    } catch (e: IOException) {
        -1
    }

    fun desconectar() {
        try {
            servidor?.close()
            socket?.close()
        } catch (e: IOException) {
            // ya estaba cerrado
        }
        servidor = null
        socket = null
        lector = null
    }
}

// Base de datos SQLite: usuarios (contraseñas cifradas) e historial de eventos
class BaseDatos(context: Context) : SQLiteOpenHelper(context, "CondominioIoT.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE usuarios (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "username TEXT UNIQUE NOT NULL, " +
                "salt TEXT NOT NULL, " +
                "password_hash TEXT NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE telemetria (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "fecha_hora TEXT NOT NULL, " +
                "evento TEXT NOT NULL)"
        )
        // Usuario por defecto: admin / 1234
        insertarUsuario(db, "admin", "1234")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS usuarios")
        db.execSQL("DROP TABLE IF EXISTS telemetria")
        onCreate(db)
    }

    fun registrarUsuario(usuario: String, clave: String): Boolean =
        insertarUsuario(writableDatabase, usuario, clave)

    // Consulta parametrizada (?) para evitar inyección SQL
    fun validarUsuario(usuario: String, clave: String): Boolean {
        readableDatabase.rawQuery(
            "SELECT salt, password_hash FROM usuarios WHERE username = ?",
            arrayOf(usuario)
        ).use { cursor ->
            if (!cursor.moveToFirst()) return false
            val salt = cursor.getString(0)
            val hashGuardado = cursor.getString(1)
            return MessageDigest.isEqual(
                cifrar(clave, salt).toByteArray(),
                hashGuardado.toByteArray()
            )
        }
    }

    private fun insertarUsuario(db: SQLiteDatabase, usuario: String, clave: String): Boolean {
        val salt = generarSalt()
        val valores = ContentValues().apply {
            put("username", usuario)
            put("salt", salt)
            put("password_hash", cifrar(clave, salt))
        }
        return db.insert("usuarios", null, valores) != -1L
    }

    // Salt aleatorio: dos usuarios con la misma clave tienen hashes distintos
    private fun generarSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // SHA-256: la contraseña nunca se guarda en texto plano (ISO 27400)
    private fun cifrar(clave: String, salt: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest((salt + clave).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun guardarEvento(evento: String) {
        println("[LOG] $evento") // También aparece en Logcat
        val fecha = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()).format(Date())
        val valores = ContentValues().apply {
            put("fecha_hora", fecha)
            put("evento", evento)
        }
        writableDatabase.insert("telemetria", null, valores)
    }

    fun obtenerHistorial(): List<String> {
        val lista = mutableListOf<String>()
        readableDatabase.rawQuery(
            "SELECT fecha_hora, evento FROM telemetria ORDER BY id DESC LIMIT 50",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                lista.add("[${cursor.getString(0)}] ${cursor.getString(1)}")
            }
        }
        return lista
    }

    fun limpiarHistorial() {
        writableDatabase.delete("telemetria", null, null)
    }
}
