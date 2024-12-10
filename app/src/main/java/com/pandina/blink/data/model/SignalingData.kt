package com.pandina.blink.data.model

data class SignalingData(
    val type: String, // "offer", "answer", o "candidate"
    val sdp: String,
    val candidate: String? = null,
    val sdpMid: String? = null,
    val sdpMLineIndex: Int? = null
)
