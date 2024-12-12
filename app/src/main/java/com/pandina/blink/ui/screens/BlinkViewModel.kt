package com.pandina.blink.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pandina.blink.data.repository.SignalingRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.webrtc.Camera2Enumerator
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.EglBase
import org.webrtc.HardwareVideoEncoderFactory
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SimulcastVideoEncoderFactory
import org.webrtc.SoftwareVideoEncoderFactory
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoTrack
import java.util.UUID

class BlinkViewModel(application: Application) : AndroidViewModel(application) {
    private val userId = UUID.randomUUID().toString()
    private val appContext = application.applicationContext

    private val _remoteVideoCall = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoCall: StateFlow<VideoTrack?> = _remoteVideoCall.asStateFlow()
    private val signalingRepository = SignalingRepository(userId)

    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    val eglBase = EglBase.create()

    private lateinit var videoCapturer: VideoCapturer
    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrack: StateFlow<VideoTrack?> = _localVideoTrack.asStateFlow()
    private var iceCandidatesJob: Job? = null

    init {
        initializePeerConnectionFactory()
        initializeLocalVideoTrack()
        initializePeerConnection()
        addLocalMediaTracks()
        signaling()
    }

    private val videoDecoderFactory =
        DefaultVideoDecoderFactory(
            eglBase.eglBaseContext
        )


    private val videoEncoderFactory =
        SimulcastVideoEncoderFactory(HardwareVideoEncoderFactory(eglBase.eglBaseContext, true, true), SoftwareVideoEncoderFactory())


    // WEB RTC CONFIGURATION
    private fun initializePeerConnectionFactory() {
        // Esto corre en el hilo principal
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(appContext)
                .createInitializationOptions()
        )

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoDecoderFactory(videoDecoderFactory)
            .setVideoEncoderFactory(videoEncoderFactory)
            .createPeerConnectionFactory()
    }


    private fun initializePeerConnection() {
        val rtcConfig = PeerConnection.RTCConfiguration(
            listOf(
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
                //PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
            )
        )
        peerConnection =
            peerConnectionFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
                override fun onIceCandidate(candidate: IceCandidate?) {
                    candidate?.let { viewModelScope.launch { signalingRepository.sendIceCandidate(it) } }
                }

                override fun onTrack(transceiver: RtpTransceiver?) {
                    val videoTrack = transceiver?.receiver?.track() as? VideoTrack
                    videoTrack?.let { _remoteVideoCall.value = it }
                }

                override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {}
                override fun onSignalingChange(newState: PeerConnection.SignalingState?) {
                }

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
                            println("ICE Connection: Conexión cerrada")
                        }

                        else -> {
                            println("ICE Connection: Estado desconocido $newState")
                        }
                    }
                }

                override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {}
                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
                override fun onAddStream(stream: MediaStream?) {}
                override fun onRemoveStream(stream: MediaStream?) {}
                override fun onDataChannel(channel: DataChannel?) {}
                override fun onRenegotiationNeeded() {}
                override fun onIceConnectionReceivingChange(receiving: Boolean) {
                    println("onIceConnectionReceivingChange: $receiving")
                }
            }) ?: throw IllegalStateException("Failed to create PeerConnection")
    }

    fun signaling() {
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
    private fun initializeLocalVideoTrack() {
        val videoSource = peerConnectionFactory.createVideoSource(false)

        videoCapturer = createCameraCapturer()
        videoCapturer.initialize(
            SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext),
            appContext,
            videoSource.capturerObserver
        )

        videoCapturer.startCapture(1280, 720, 30)

        _localVideoTrack.value =
            peerConnectionFactory.createVideoTrack("LOCAL_VIDEO_TRACK", videoSource)
    }

    private fun createCameraCapturer(): VideoCapturer {
        val cameraEnumerator = Camera2Enumerator(appContext)
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

    private fun addLocalMediaTracks() {
        try {
            // Solo añadimos la pista de video
            _localVideoTrack.value?.let { videoTrack ->
                peerConnection?.addTrack(videoTrack, listOf("local_stream"))
            } ?: run {
                println("Error: LocalVideoTrack no está inicializado")
            }

            // Configurar y Agregar AudioTrack
            val audioConstraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            }
            val audioSource = peerConnectionFactory.createAudioSource(audioConstraints)
            val audioTrack = peerConnectionFactory.createAudioTrack("LOCAL_AUDIO_TRACK", audioSource)

            peerConnection?.addTrack(audioTrack, listOf("local_stream"))

            println("Audio y Video Tracks agregados al PeerConnection")
        } catch (e: Exception) {
            println("Error al agregar Tracks al PeerConnection: ${e.message}")
        }
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
        eglBase.release()

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

