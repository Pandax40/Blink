package com.pandina.blink.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pandina.blink.data.repository.SignalingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
    private val eglBase = EglBase.create()

    init {
        initializePeerConnectionFactory()
        initializePeerConnection()
        start()
    }

    private fun initializePeerConnectionFactory() {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(appContext)
                .createInitializationOptions()
        )
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .createPeerConnectionFactory()
    }

    private fun initializePeerConnection() {
        val rtcConfig = PeerConnection.RTCConfiguration(
            listOf(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
                PeerConnection.IceServer.builder("stun:stun1.l.google.com:3478").createIceServer(),
                PeerConnection.IceServer.builder("stun:stun2.l.google.com:5349").createIceServer(),
                PeerConnection.IceServer.builder("stun:stun4.l.google.com:19302").createIceServer(),
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
                    if (newState == PeerConnection.SignalingState.STABLE) {
                        viewModelScope.launch {
                            signalingRepository.getIceCandidates().collect { iceCandidate ->
                                peerConnection?.addIceCandidate(iceCandidate)
                            }
                        }
                    }
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
        //addLocalMediaStream()
    }

    /*private fun addLocalMediaStream() {
        val audioTrack = peerConnectionFactory.createAudioTrack(
            "audio_track", peerConnectionFactory.createAudioSource(MediaConstraints())
        )
        val videoTrack = peerConnectionFactory.createVideoTrack(
            "video_track", peerConnectionFactory.createVideoSource(false)
        )

        val mediaStream = peerConnectionFactory.createLocalMediaStream("local_stream")
        mediaStream.addTrack(audioTrack)
        mediaStream.addTrack(videoTrack)

        peerConnection?.addStream(mediaStream)
    }*/


    fun start() {
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
                                viewModelScope.launch {
                                    peerConnection?.setLocalDescription(
                                        SimpleSdpObserver(),
                                        offerSdp
                                    )
                                    signalingRepository.setOffer(offerSdp.description)
                                        .collect { awnserSdp ->
                                            peerConnection?.setRemoteDescription(
                                                SimpleSdpObserver(),
                                                awnserSdp
                                            )
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
                                    peerConnection?.setLocalDescription(
                                        SimpleSdpObserver(),
                                        awnserSdp
                                    )
                                    signalingRepository.sendAwnser(awnserSdp.description)
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

    override fun onCleared() {
        super.onCleared()
        runBlocking { signalingRepository.close() }
    }
}

class SimpleSdpObserver : SdpObserver {
    override fun onSetSuccess() {}
    override fun onSetFailure(error: String?) {}
    override fun onCreateSuccess(description: SessionDescription?) {}
    override fun onCreateFailure(error: String?) {}
}