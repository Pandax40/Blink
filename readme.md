# Blink - Video Chat Aleatorio

Blink es una aplicación móvil de chat uno a uno, similar a Omegle, que conecta a usuarios de manera aleatoria a través de video y voz. La aplicación utiliza la API de WebRTC para la transmisión de medios y Firebase como servidor de señalización.

## Características principales

- **Chat de video y voz**: Comunicación en tiempo real entre dos usuarios seleccionados al azar.
- **Cambio de participante**: Permite buscar otro usuario con un solo clic.
- **Interfaz intuitiva**: Diseño simple y fácil de usar basado en Jetpack Compose.

## Requisitos del sistema

- Android 5.0 (Lollipop) o superior.
- Acceso a Internet estable.
- Cámara y micrófono funcionales.

## Tecnologías utilizadas

- **Lenguaje**: Kotlin.
- **Arquitectura**: MVVM (Model-View-ViewModel).
- **Streaming de video y voz**: WebRTC (GetStream API).
- **Servidor de señalización**: Firebase Firestore.
- **UI**: Jetpack Compose.

## Instalación y configuración

### 1. Clonar el repositorio

```bash
git clone https://github.com/Pandax40/Blink.git
cd Blink
```

### 2. Configurar el entorno

1. Crea un proyecto en Firebase y habilita Firestore.
2. Configura las reglas de Firestore para permitir acceso seguro.
3. Descarga el archivo `google-services.json` y colócalo en el directorio `app` de tu proyecto.

### 3. Configurar WebRTC

El proyecto ya incluye configuraciones básicas de WebRTC:
- Servidores STUN y TURN están configurados en el archivo `Client.kt`.
- El servidor TURN es gratuito y no tiene mucha capacidad, no supone un riesgo exponerlo en el repositorio.

Si necesitas personalizar los servidores de STUN/TURN, actualiza los valores en `Client.kt` en la sección:

```kotlin
private val iceServers = listOf(
    PeerConnection.IceServer.builder("stun:stun.relay.metered.ca:80").createIceServer(),
    // Otros servidores TURN/STUN...
)
```

### 4. Compilar y ejecutar

1. Abre el proyecto en Android Studio.
2. Sincroniza las dependencias de Gradle.
3. Compila y empaqueta la aplicación.
4. Distribuye la aplicación.

## Uso

1. Al iniciar la aplicación, verás un botón de **Blink**.
2. Haz clic en el botón para conectarte con otro usuario aleatorio.
3. Puedes finalizar la sesión y buscar otro usuario haciendo clic en **Blink Again**.
4. Finalmente puedes salir de la session volviendo al menu principal desde el boton superior izquierdo.

## Estructura del proyecto

- **HomeScreen.kt**: Pantalla de inicio con el botón para iniciar un chat.
- **BlinkScreen.kt**: Pantalla principal donde se muestra el video de ambos participantes.
- **BlinkViewModel.kt**: Lógica de presentación y gestión de estados de video y señalización.
- **Client.kt**: Configuración de WebRTC y gestión de PeerConnection.
- **AudioController.kt**: Gestión de dispositivos de audio.
- **SignalingRepository.kt**: Interacciones con Firebase para la señalización.
