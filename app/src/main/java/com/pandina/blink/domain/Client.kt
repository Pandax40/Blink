package com.pandina.blink.domain

import android.app.Application
import com.pandina.blink.data.repository.SignalingRepository
import com.pandina.blink.ui.viewmodel.SimpleSdpObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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

class Client(application: Application, private val viewModelScope: CoroutineScope, private val onAdd: (MediaStream?) -> Unit, private val onClose: () -> Unit, private val rootEglBase: EglBase) {
    private val userId = UUID.randomUUID().toString()

    companion object {
        private const val LOCAL_TRACK_ID = "local_track"
        private const val LOCAL_STREAM_ID = "local_track"
    }

    private val signalingRepository = SignalingRepository(userId)

    private val peerConnectionFactory by lazy {
        initializePeerConnectionFactory(application)
    }

    private val localVideoSource by lazy {
        peerConnectionFactory.createVideoSource(false)
    }

    private val audioSource by lazy {
        peerConnectionFactory.createAudioSource(audioConstraints)
    }

    private lateinit var videoCapturer: VideoCapturer

    val localAudioTrack: AudioTrack? by lazy {
        peerConnectionFactory.createAudioTrack(LOCAL_TRACK_ID + "_audio", audioSource)
    }

    val localVideoTrack: VideoTrack? by lazy {
        peerConnectionFactory.createVideoTrack(LOCAL_TRACK_ID, localVideoSource)
    }

    private lateinit var peerConnection: PeerConnection

    private val audioConstraints = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
    }
    private val mediaConstraints = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
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

    private var iceCandidatesJob: Job? = null

    private val observer: PeerConnection.Observer = object : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate?) {
            candidate?.let {
                runBlocking {
                    signalingRepository.sendIceCandidate(it)
                }
                peerConnection.addIceCandidate(it)
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
                    onClose()
                    println("ICE Connection: La conexión ha fallado")
                }

                PeerConnection.IceConnectionState.DISCONNECTED -> {
                    onClose()
                    println("ICE Connection: Conexión perdida")
                }

                PeerConnection.IceConnectionState.CLOSED -> {
                    onClose()
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
            onAdd(stream)
        }

        override fun onRemoveStream(stream: MediaStream?) {}
        override fun onDataChannel(channel: DataChannel?) {}
        override fun onRenegotiationNeeded() {}
        override fun onIceConnectionReceivingChange(receiving: Boolean) {
            println("onIceConnectionReceivingChange: $receiving")
        }
    }

    init {
        startLocalVideoCapture(application)
    }

    private fun initPeerConnectionFactory(context: Application) {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
    }

    private fun initializePeerConnectionFactory(application: Application): PeerConnectionFactory {
        initPeerConnectionFactory(application)

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
        peerConnectionFactory.createPeerConnection(iceServers, observer)?.let {
            peerConnection = it
        }
        peerConnection.addTrack(localVideoTrack, listOf(this).map { LOCAL_STREAM_ID })
        peerConnection.addTrack(localAudioTrack, listOf(this).map { LOCAL_STREAM_ID })
    }

    private fun startLocalVideoCapture(application: Application) {
        val surfaceTextureHelper =
            SurfaceTextureHelper.create(Thread.currentThread().name, rootEglBase.eglBaseContext)
        videoCapturer = createCameraCapturer(application)
        videoCapturer.initialize(
            surfaceTextureHelper,
            application,
            localVideoSource.capturerObserver
        )

        videoCapturer.startCapture(1280, 720, 30)
    }

    private fun createCameraCapturer(application: Application): VideoCapturer {
        val cameraEnumerator = Camera2Enumerator(application)
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

    fun signaling() {
        iceCandidatesJob?.cancel()
        iceCandidatesJob = null

        runBlocking {
            signalingRepository.close()
        }

        try{
            peerConnection.close()
            peerConnection.dispose()
        } catch (e: Exception) {
            println("Error al cerrar la conexión: ${e.message}")
        }
        initializePeerConnection()

        val type: SessionDescription.Type
        runBlocking {
            type = signalingRepository.revealSignalingType()
        }
        if (type == SessionDescription.Type.OFFER) {
            peerConnection.createOffer(object : SdpObserver {
                override fun onCreateSuccess(offerSdp: SessionDescription?) {
                    offerSdp?.let {
                        peerConnection.setLocalDescription(
                            SimpleSdpObserver(), offerSdp
                        )
                        viewModelScope.launch {
                            signalingRepository.setOffer(offerSdp.description)
                                .collect { answerSdp ->
                                    peerConnection.setRemoteDescription(
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
            val remoteOffer: SessionDescription?
            runBlocking {
                remoteOffer = signalingRepository.getOffer()
            }
            peerConnection.setRemoteDescription(SimpleSdpObserver(), remoteOffer)
            peerConnection.createAnswer(object : SdpObserver {
                override fun onCreateSuccess(awnserSdp: SessionDescription?) {
                    awnserSdp?.let {
                        runBlocking {
                            signalingRepository.sendAwnser(awnserSdp.description) //Enviar antes para tener el remoteUserId
                            peerConnection.setLocalDescription(
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
    }

    private fun startIceCandidateCollection() {
        // Evitar iniciar la recolección más de una vez
        if (iceCandidatesJob == null) {
            iceCandidatesJob = viewModelScope.launch {
                signalingRepository.getIceCandidates().collect { iceCandidate ->
                    peerConnection.addIceCandidate(iceCandidate)
                }
            }
        }
    }

    fun close() {
        iceCandidatesJob?.cancel()
        iceCandidatesJob = null

        runBlocking {
            signalingRepository.close()
        }

        try {
            videoCapturer.stopCapture()
            videoCapturer.dispose()
        } catch (e: Exception) {
            println("Error al detener la captura de video: ${e.message}")
        }

        try {
            peerConnection.close()
            peerConnection.dispose()
        } catch (e: Exception) {
            println("Error al cerrar la conexión: ${e.message}")
        }

        rootEglBase.release()
    }
}