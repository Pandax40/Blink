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
- **Autenticación**: Firebase Authentication (Autenticación Anónima).

## Instalación y configuración

### 1. Clonar el repositorio

```bash
git clone https://github.com/Pandax40/Blink.git
cd Blink
```

### 2. Configurar Firebase

1. Crea un proyecto en [Firebase](https://console.firebase.google.com/)
2. Habilita **Firestore** en modo seguro y asegúrate de añadir las siguientes reglas:
```txt
rules_version = '2';

service cloud.firestore {
  match /databases/{database}/documents {

    // Regla para la colección waitingRoom
    match /waitingRoom/current {
    	// Solo usuarios autenticados pueden leer
      allow read: if request.auth != null;
      // Solo usuarios autenticados pueden escribir y solo el campo roomId es permitido
      allow write: if request.auth != null && request.resource.data.keys().hasOnly(['roomId']);
      // Solo usuarios autenticados pueden borrar el waitingRoom
      allow delete: if request.auth != null
    }

    // Regla para la colección rooms
    match /rooms/{roomId} {
    	// Solo usuarios autenticados pueden leer las salas
      allow read: if request.auth != null;
      // Solo usuarios autenticados pueden crear salas con los campos offer y answer
      allow create: if request.auth != null && request.resource.data.keys().hasOnly(['offer', 'answer']);
      allow update: if request.auth != null && (
        // Los campos válidos son offer o answer
        (request.resource.data.offer is map && request.resource.data.offer.keys().hasOnly(['sdp', 'ownerId'])) ||
        (request.resource.data.answer is map && request.resource.data.answer.keys().hasOnly(['sdp', 'responderId']))
      );
      // Permitir que usuarios autenticados eliminen salas
      allow delete: if request.auth != null;
    }

    // Regla para la colección iceCandidates
    match /iceCandidates/{userId} {
    	// Un usuario solo puede leer todos los candidatos
      allow read: if request.auth != null; 
      // Un usuario solo puede escribir sus propios candidatos
      allow write: if request.auth != null && request.auth.uid == userId; 
      // Un usuario solo puede borrar sus propios candidatos
      allow delete: if request.auth != null  && request.auth.uid == userId; 
    }
  }
}
```
3. Habilita **Autenticación Anónima** en la consola de Firebase:
   - Ve a **Authentication > Métodos de inicio de sesión**.
   - Activa la opción **Anónimo**.
4. Descarga el archivo `google-services.json` desde Firebase y colócalo en el directorio `app` de tu proyecto.

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
3. Compila y ejecuta la aplicación.

## Uso

1. Al iniciar la aplicación, el usuario será autenticado automáticamente mediante **Autenticación Anónima**.
2. Verás un botón de **Blink** en la pantalla principal.
3. Haz clic en el botón para conectarte con otro usuario aleatorio.
4. Puedes finalizar la sesión y buscar otro usuario haciendo clic en **Blink Again**.
5. Si deseas salir de la sesión, utiliza el botón superior izquierdo para regresar al menú principal.

## Estructura del proyecto

- **HomeScreen.kt**: Pantalla de inicio con el botón para iniciar un chat.
- **BlinkScreen.kt**: Pantalla principal donde se muestra el video de ambos participantes.
- **BlinkViewModel.kt**: Lógica de presentación y gestión de estados de video y señalización.
- **Client.kt**: Configuración de WebRTC y gestión de PeerConnection.
- **AudioController.kt**: Gestión de dispositivos de audio.
- **SignalingRepository.kt**: Interacciones con Firebase para la señalización.
