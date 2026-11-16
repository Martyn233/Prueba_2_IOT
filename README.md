# Alumbrado Condominio IoT

App Android (Kotlin + Jetpack Compose) para monitorear y controlar las luces de un condominio.
Un nodo IoT (Arduino UNO + sensor LDR + HC-05, o un segundo teléfono en "Modo nodo")
enciende las luces según la oscuridad, y la app permite forzarlas manualmente y ver su estado.

TI3042 Aplicaciones Móviles para IoT · Evaluación Sumativa Unidad 2

## Funcionalidades

- Login y registro de operadores (SQLite, contraseñas con SHA-256 + salt).
- Conexión Bluetooth clásica (SPP) con el HC-05 o con otro teléfono Android.
- Comandos: `A` automático (sensor LDR), `1` encender, `0` apagar.
- Telemetría en tiempo real (`[TELEMETRIA] Luces: ON | Modo: AUTO`).
- Historial de eventos con fecha y hora guardado en SQLite.
- Modo nodo: un segundo teléfono simula el tablero (Arduino + HC-05).
- Modo simulación cuando no hay nada conectado (sirve en el emulador).

## Cómo probarlo

1. Instalar la app en dos teléfonos Android y vincularlos en Ajustes > Bluetooth.
2. Entrar con el usuario `admin` y la contraseña `1234`.
3. Teléfono 2: botón **Modo nodo** > **Iniciar nodo**.
4. Teléfono 1: elegir el teléfono 2 en la lista > **Conectar**.
5. Usar los botones `A`, `1` y `0`, y el interruptor "Sensor LDR tapado" del teléfono 2.

## Seguridad (ISO/IEC 27400)

- Contraseñas nunca en texto plano: SHA-256 con salt aleatorio por usuario.
- Control de acceso: sin login no se accede al panel ni al modo nodo.
- Permiso `BLUETOOTH_CONNECT` pedido en tiempo de ejecución (Android 12+).
- Consultas SQL parametrizadas contra inyección SQL.
- Bluetooth requiere vinculación previa con PIN.

## Capturas

_(Agregar aquí capturas del login, el panel y el modo nodo.)_
