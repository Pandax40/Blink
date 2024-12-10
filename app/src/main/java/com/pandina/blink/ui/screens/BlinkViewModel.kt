package com.pandina.blink.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pandina.blink.data.remote.SignalingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.webrtc.VideoTrack

class BlinkViewModel : ViewModel() {
    private val _remoteVideoCall = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoCall: StateFlow<VideoTrack?> = _remoteVideoCall.asStateFlow()
    private val signalingRepository = SignalingRepository()

    fun getRemoteDescription() {
        viewModelScope.launch {
            try {
                signalingRepository.getRemoteDescription("TEST_SPD").collect { signalingData ->
                    // Aquí puedes realizar acciones adicionales si es necesario
                    println("Remote SDP recibido: ${signalingData.sdp}")
                }
            } catch (e: Exception) {
                println("Error en el signaling: ${e.message}")
            }
        }
    }

}