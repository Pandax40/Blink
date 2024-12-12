package com.pandina.blink.data.repository

import com.google.firebase.firestore.ListenerRegistration
import com.pandina.blink.data.remote.FirebaseService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

class SignalingRepository(private val userId: String) {
    private val firebaseService: FirebaseService = FirebaseService(userId)
    private var listener: ListenerRegistration? = null
    private var roomId: String? = null
    private var remoteUserId: String? = null
    private lateinit var currentType: SessionDescription.Type

    suspend fun revealSignalingType(): SessionDescription.Type {
        close()
        roomId = firebaseService.getAndRemoveWaitingRoom()
        currentType = if (roomId != null) {
            SessionDescription.Type.ANSWER
        } else {
            SessionDescription.Type.OFFER
        }
        return currentType
    }

    fun setOffer(offerSdp: String): Flow<SessionDescription> = callbackFlow {
        val roomIdValid = firebaseService.createWaitingRoom(offerSdp)
        roomId = roomIdValid

        listener =
            firebaseService.listenForAnswer(
                roomId = roomIdValid,
                onAnswerReceived = { room ->
                    remoteUserId = room.responderId
                    trySend(
                        SessionDescription(
                            SessionDescription.Type.ANSWER,
                            room.awnserSdp
                        )
                    )
                })
        awaitClose{}
    }

    suspend fun sendAwnser(awnserSdp: String) {
        roomId?.let { existingRoomId ->
            val roomOffer = firebaseService.connectToRoom(existingRoomId, awnserSdp)
            remoteUserId = roomOffer?.ownerId
        }
    }

    fun getIceCandidates(): Flow<IceCandidate> = callbackFlow {
        try {
            remoteUserId?.let { ownerId ->
                listener =
                    firebaseService.listenForIceCandidate(ownerId) { candidate, sdpMid, sdpMLineIndex ->
                        trySend(IceCandidate(sdpMid, sdpMLineIndex, candidate))
                    }
            }
            awaitClose { }
        } catch (e: Exception) {
            close(e)
        }
    }

    suspend fun getOffer(): SessionDescription? {
        roomId?.let { existingRoomId ->
            return firebaseService.getOfferSdp(existingRoomId)
        }
        return null
    }

    suspend fun sendIceCandidate(candidate: IceCandidate) {
        firebaseService.addIceCandidate(
            userId,
            candidate.sdp,
            candidate.sdpMid,
            candidate.sdpMLineIndex
        )
    }

    suspend fun close() {
        listener?.remove()
        firebaseService.deleteIceCandidates()
        roomId?.let { roomId ->
            if (firebaseService.getWaitingRoom() == roomId)
                firebaseService.remoteWaitingRoom()
            firebaseService.deleteRoom(roomId)
        }
        roomId = null
        remoteUserId = null
    }
}
