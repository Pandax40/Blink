package com.pandina.blink.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pandina.blink.domain.AudioController
import com.pandina.blink.domain.Client
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val onClose: () -> Unit = {
        if(blinkOnClose)
            blink()
    }

    private val client by lazy { Client(application, viewModelScope, onAdd, onClose, rootEglBase) }

    private val _localAudioTrack = MutableStateFlow<AudioTrack?>(null)
    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrack: StateFlow<VideoTrack?> = _localVideoTrack.asStateFlow()

    private val _remoteVideoCall = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoCall: StateFlow<VideoTrack?> = _remoteVideoCall.asStateFlow()

    private val audioController: AudioController by lazy { AudioController.create(application) }
    private var blinkOnClose: Boolean = true

    init {
        audioController.selectAudioDevice(AudioController.AudioDevice.SPEAKER_PHONE)
        audioController.setDefaultAudioDevice(AudioController.AudioDevice.SPEAKER_PHONE)
        _localAudioTrack.value = client.localAudioTrack
        _localVideoTrack.value = client.localVideoTrack
        client.signaling()
    }


    fun blink() {
        blinkOnClose = false
        _remoteVideoCall.value = null
        client.signaling()
        blinkOnClose = true
    }

    override fun onCleared() {
        _remoteVideoCall.value = null
        _localVideoTrack.value = null
        client.close()
    }
}

class SimpleSdpObserver : SdpObserver {
    override fun onSetSuccess() {}
    override fun onSetFailure(error: String?) {}
    override fun onCreateSuccess(description: SessionDescription?) {}
    override fun onCreateFailure(error: String?) {}
}

