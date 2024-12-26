package com.pandina.blink.ui.screens

import android.app.Application
import android.content.Context
import android.media.AudioManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pandina.blink.data.repository.SignalingRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoTrack
import java.util.UUID

class BlinkViewModel(application: Application) : AndroidViewModel(application) {
    private val userId = UUID.randomUUID().toString()

    private val app = application

    companion object {
        private const val LOCAL_TRACK_ID = "local_track"
        private const val LOCAL_STREAM_ID = "local_track"
    }

    private val signalingRepository = SignalingRepository(userId)

    var rootEglBase: EglBase = EglBase.create()

    private lateinit var videoCapturer: VideoCapturer
    private val _localAudioTrack = MutableStateFlow<AudioTrack?>(null)
    val localAudioTrack: StateFlow<AudioTrack?> = _localAudioTrack.asStateFlow()
    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrack: StateFlow<VideoTrack?> = _localVideoTrack.asStateFlow()

    private val _remoteVideoCall = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoCall: StateFlow<VideoTrack?> = _remoteVideoCall.asStateFlow()

    private val peerConnectionFactory by lazy { initializePeerConnectionFactory() }

    private val audioSource by lazy { peerConnectionFactory.createAudioSource(audioConstraints)}
    private val localVideoSource by lazy { peerConnectionFactory.createVideoSource(false) }
    private var peerConnection: PeerConnection? = null

    private var iceCandidatesJob: Job? = null

    private val audioConstraints = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
    }
    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.relay.metered.ca:80").createIceServer(),
        PeerConnection.IceServer.builder("turn:global.relay.metered.ca:80")
            .setUsername("b1820be959f3ea5c0c79e71d").setPassword("kylAgxVKFT90ow1L")
            .createIceServer(),
        PeerConnection.IceServer.builder("turn:global.relay.metered.ca:80?transport=tcp")
            .setUsername("b1820be959f3ea5c0c79e71d").setPassword("kylAgxVKFT90ow1L")
            .createIceServer(),
        PeerConnection.IceServer.builder("turn:global.relay.metered.ca:443")
            .setUsername("b1820be959f3ea5c0c79e71d").setPassword("kylAgxVKFT90ow1L")
            .createIceServer(),
        PeerConnection.IceServer.builder("turns:global.relay.metered.ca:443?transport=tcp")
            .setUsername("b1820be959f3ea5c0c79e71d").setPassword("kylAgxVKFT90ow1L")
            .createIceServer(),
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
    )

    init {
        initPeerConnectionFactory(application)
        initializePeerConnection()
        startLocalVideoCapture()
        signaling()

        // TODO: Mover una clase especifica para manejar el audio (capa dominio)
        val audioManager = application.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_NORMAL
        audioManager.isSpeakerphoneOn = true
    }

    private fun initPeerConnectionFactory(context: Application) {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
    }

    private fun initializePeerConnectionFactory(): PeerConnectionFactory {
        // Esto corre en el hilo principal
        val options = PeerConnectionFactory.InitializationOptions.builder(app)
            .setEnableInternalTracer(true)
            .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        return PeerConnectionFactory.builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(rootEglBase.eglBaseContext))
            .setVideoEncoderFactory(
                DefaultVideoEncoderFactory(
                    rootEglBase.eglBaseContext,
                    true,
                    true
                )
            )
            .setOptions(PeerConnectionFactory.Options().apply {
                disableEncryption = true      //Change if works with encryption
                disableNetworkMonitor = true
            })
            .createPeerConnectionFactory()
    }


    private fun initializePeerConnection() {
        peerConnection =
            peerConnectionFactory.createPeerConnection(iceServers, object : PeerConnection.Observer {
                override fun onIceCandidate(candidate: IceCandidate?) {
                    candidate?.let {
                        viewModelScope.launch {
                            signalingRepository.sendIceCandidate(it)
                        }
                        peerConnection?.addIceCandidate(it)
                    }

                }

                override fun onTrack(transceiver: RtpTransceiver?) {}

                override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {}
                override fun onSignalingChange(newState: PeerConnection.SignalingState?) {}

                override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                    when (newState) {
                        PeerConnection.IceConnectionState.CHECKING -> {
                            println("ICE Connection: Verificando conectividad")
                        }

                        PeerConnection.IceConnectionState.CONNECTED -> {
                            println("ICE Connection: Conexión establecida")
                        }

                        PeerConnection.IceConnectionState.COMPLETED -> {
                            println("ICE Connection: Negociación completada")
                            // Aquí puedes considerar que la conexión está completamente establecida.
                        }

                        PeerConnection.IceConnectionState.FAILED -> {
                            println("ICE Connection: La conexión ha fallado")
                        }

                        PeerConnection.IceConnectionState.DISCONNECTED -> {
                            println("ICE Connection: Conexión perdida")
                        }

                        PeerConnection.IceConnectionState.CLOSED -> {
                            //blink()
                            println("ICE Connection: Conexión cerrada")
                        }

                        else -> {
                            println("ICE Connection: Estado desconocido $newState")
                        }
                    }
                }

                override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {}
                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
                override fun onAddStream(stream: MediaStream?) {
                    stream?.videoTracks?.firstOrNull()?.let {
                        _remoteVideoCall.value = it
                    }
                }

                override fun onRemoveStream(stream: MediaStream?) {}
                override fun onDataChannel(channel: DataChannel?) {}
                override fun onRenegotiationNeeded() {}
                override fun onIceConnectionReceivingChange(receiving: Boolean) {
                    println("onIceConnectionReceivingChange: $receiving")
                }
            }) ?: throw IllegalStateException("Failed to create PeerConnection")
    }

    private fun signaling() {
        val mediaConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
        viewModelScope.launch {
            try {
                val type = signalingRepository.revealSignalingType()
                if (type == SessionDescription.Type.OFFER) {
                    peerConnection?.createOffer(object : SdpObserver {
                        override fun onCreateSuccess(offerSdp: SessionDescription?) {
                            offerSdp?.let {
                                peerConnection?.setLocalDescription(
                                    SimpleSdpObserver(), offerSdp
                                )
                                viewModelScope.launch {
                                    signalingRepository.setOffer(offerSdp.description)
                                        .collect { answerSdp ->
                                            peerConnection?.setRemoteDescription(
                                                SimpleSdpObserver(), answerSdp
                                            )
                                            startIceCandidateCollection()
                                        }
                                }
                            }
                        }

                        override fun onSetSuccess() {}

                        override fun onCreateFailure(error: String?) {
                            println("Error al crear la oferta: $error")
                        }

                        override fun onSetFailure(error: String?) {}
                    }, mediaConstraints)
                } else {
                    val remoteOffer = signalingRepository.getOffer()
                    peerConnection?.setRemoteDescription(SimpleSdpObserver(), remoteOffer)
                    peerConnection?.createAnswer(object : SdpObserver {
                        override fun onCreateSuccess(awnserSdp: SessionDescription?) {
                            awnserSdp?.let {
                                viewModelScope.launch {
                                    signalingRepository.sendAwnser(awnserSdp.description) //Enviar antes para tener el remoteUserId
                                    peerConnection?.setLocalDescription(
                                        SimpleSdpObserver(), awnserSdp
                                    )
                                    startIceCandidateCollection()
                                }
                            }
                        }

                        override fun onSetSuccess() {}

                        override fun onCreateFailure(error: String?) {
                            println("Error al crear la respuesta: $error")
                        }

                        override fun onSetFailure(error: String?) {}
                    }, mediaConstraints)
                }
            } catch (e: Exception) {
                println("Error en el signaling: ${e.message}")
            }
        }
    }

    private fun startIceCandidateCollection() {
        // Evitar iniciar la recolección más de una vez
        if (iceCandidatesJob == null) {
            iceCandidatesJob = viewModelScope.launch {
                signalingRepository.getIceCandidates().collect { iceCandidate ->
                    peerConnection?.addIceCandidate(iceCandidate)
                }
            }
        }
    }

    // Video Configuration
    private fun startLocalVideoCapture() {
        val surfaceTextureHelper =
            SurfaceTextureHelper.create(Thread.currentThread().name, rootEglBase.eglBaseContext)
        videoCapturer = createCameraCapturer()
        videoCapturer.initialize(
            surfaceTextureHelper,
            app,
            localVideoSource.capturerObserver
        )

        videoCapturer.startCapture(1280, 720, 30)

        // Crear la pista de video local
        _localVideoTrack.value =
            peerConnectionFactory.createVideoTrack(LOCAL_TRACK_ID, localVideoSource)
        _localAudioTrack.value =
            peerConnectionFactory.createAudioTrack(LOCAL_TRACK_ID + "_audio", audioSource);

        // Añadir la pista de video al PeerConnection
        _localVideoTrack.value?.let { videoTrack ->
            val mediaStreamTrackList = listOf(videoTrack)
            peerConnection?.addTrack(videoTrack, mediaStreamTrackList.map { LOCAL_STREAM_ID })
        }
        _localAudioTrack.value?.let { audioTrack ->
            val mediaStreamTrackList = listOf(audioTrack)
            peerConnection?.addTrack(audioTrack, mediaStreamTrackList.map { LOCAL_STREAM_ID })
        }
    }

    private fun createCameraCapturer(): VideoCapturer {
        val cameraEnumerator = Camera2Enumerator(app)
        val deviceNames = cameraEnumerator.deviceNames

        for (deviceName in deviceNames) {
            if (cameraEnumerator.isFrontFacing(deviceName)) {
                return cameraEnumerator.createCapturer(deviceName, null) ?: continue
            }
        }

        for (deviceName in deviceNames) {
            if (cameraEnumerator.isBackFacing(deviceName)) {
                return cameraEnumerator.createCapturer(deviceName, null) ?: continue
            }
        }

        throw IllegalStateException("No se encontró ninguna cámara disponible")
    }

    fun blink() {
        // Limpiar trabajos de candidatos ICE
        runBlocking {
            signalingRepository.close()
        }
        iceCandidatesJob?.cancel()
        iceCandidatesJob = null

        // Cerrar y liberar PeerConnection existente
        peerConnection?.close()
        peerConnection?.dispose()
        peerConnection = null

        _remoteVideoCall.value = null

        // Reiniciar el proceso de signaling
        initializePeerConnection()
        _localVideoTrack.value?.let { videoTrack ->
            val mediaStreamTrackList = listOf(videoTrack)
            peerConnection?.addTrack(videoTrack, mediaStreamTrackList.map { LOCAL_STREAM_ID })
        }
        _localAudioTrack.value?.let { audioTrack ->
            val mediaStreamTrackList = listOf(audioTrack)
            peerConnection?.addTrack(audioTrack, mediaStreamTrackList.map { LOCAL_STREAM_ID })
        }
        signaling()
    }

    override fun onCleared() {
        try {
            videoCapturer.stopCapture()
        } catch (e: Exception) {
            println("Error al detener la captura de video: ${e.message}")
        }

        iceCandidatesJob?.cancel()
        iceCandidatesJob = null

        videoCapturer.dispose()

        // Liberar la pista de video local si es que la tienes
        _localVideoTrack.value?.dispose()

        // Cerrar y liberar el PeerConnection
        peerConnection?.close()
        peerConnection?.dispose()
        peerConnection = null

        // Liberar el PeerConnectionFactory
        peerConnectionFactory.dispose()

        // Liberar el contexto EGL
        rootEglBase.release()

        // Cerrar el signaling (Firebase u otro)
        runBlocking {
            signalingRepository.close()
        }
    }


}

class SimpleSdpObserver : SdpObserver {
    override fun onSetSuccess() {}
    override fun onSetFailure(error: String?) {}
    override fun onCreateSuccess(description: SessionDescription?) {}
    override fun onCreateFailure(error: String?) {}
}

