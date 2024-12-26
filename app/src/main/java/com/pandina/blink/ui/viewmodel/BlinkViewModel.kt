package com.pandina.blink.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pandina.blink.domain.AudioController
import com.pandina.blink.domain.Client
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.webrtc.AudioTrack
import org.webrtc.EglBase
import org.webrtc.MediaStream
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack

class BlinkViewModel(application: Application) : AndroidViewModel(application) {
    val rootEglBase: EglBase = EglBase.create()

    private val onAdd: (MediaStream?) -> Unit = { stream: MediaStream? ->
        stream?.videoTracks?.firstOrNull()?.let {
            _remoteVideoCall.value = it
        }
    }

    private val client by lazy { Client(application, onAdd, rootEglBase) }

    private val _localAudioTrack = MutableStateFlow(client.localAudioTrack)
    val localAudioTrack: StateFlow<AudioTrack?> = _localAudioTrack.asStateFlow()
    private val _localVideoTrack = MutableStateFlow(client.localVideoTrack)
    val localVideoTrack: StateFlow<VideoTrack?> = _localVideoTrack.asStateFlow()

    private val _remoteVideoCall = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoCall: StateFlow<VideoTrack?> = _remoteVideoCall.asStateFlow()

    private val audioController: AudioController by lazy { AudioController.create(application) }

    init {
        audioController.selectAudioDevice(AudioController.AudioDevice.SPEAKER_PHONE)
        audioController.setDefaultAudioDevice(AudioController.AudioDevice.SPEAKER_PHONE)
        viewModelScope.launch {
            client.signaling()
        }
    }


    fun blink() {
        // Limpiar trabajos de candidatos ICE
        /*runBlocking {
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
        signaling()*/
    }

    override fun onCleared() {
        /*try {
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
        }*/
    }
}

class SimpleSdpObserver : SdpObserver {
    override fun onSetSuccess() {}
    override fun onSetFailure(error: String?) {}
    override fun onCreateSuccess(description: SessionDescription?) {}
    override fun onCreateFailure(error: String?) {}
}

