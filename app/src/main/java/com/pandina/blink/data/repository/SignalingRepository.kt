package com.pandina.blink.data.repository

import com.google.firebase.firestore.ListenerRegistration
import com.pandina.blink.data.remote.FirebaseService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

class SignalingRepository(val userId: String) {
    private val firebaseService: FirebaseService = FirebaseService(userId)
    private var listener: ListenerRegistration? = null
    private var roomId: String? = null
    private var ownerId: String? = null
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

    /**
     * Maneja la lógica de señalización para obtener la descripción remota.
     * Crea una sala si no hay ninguna disponible o se conecta a una existente.
     *
     * @param sdp La descripción SDP local (oferta o respuesta).
     * @return Un flujo (`Flow`) que emite un `SignalingData` con la descripción remota.
     */
    fun getRemoteDescription(sdp: String): Flow<SessionDescription> = callbackFlow {
        try {
            // Obtener o crear la sala
            roomId?.let { existingRoomId ->
                // Si hay una sala disponible, conectarse a ella y obtener el offerSDP
                val offerSDPNullable = firebaseService.connectToRoom(existingRoomId, sdp)
                offerSDPNullable?.let { offerSDP ->
                    // Emitir el offerSDP inmediatamente
                    trySend(SessionDescription(SessionDescription.Type.OFFER, offerSDP))
                }
                roomId = null
            } ?: run {
                // Si no hay una sala, crear una nueva
                val roomIdValid = firebaseService.createWaitingRoom(sdp)
                roomId = roomIdValid

                listener =
                    firebaseService.listenForAnswer(
                        roomId = roomIdValid,
                        onAnswerReceived = { answerSSDP, ownerIdGot ->
                            ownerId = ownerIdGot
                            trySend(SessionDescription(SessionDescription.Type.ANSWER, answerSSDP))
                        })
            }
            awaitClose { listener?.remove() }
        } catch (e: Exception) {
            close(e)
        }
    }

    fun getIceCandidates(): Flow<IceCandidate> = callbackFlow {
        try {
            ownerId?.let { ownerId ->
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

    suspend fun getOfferIfAvailable(): SessionDescription? {
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
        roomId?.let { roomId ->
            if (firebaseService.getWaitingRoom() == roomId)
                firebaseService.remoteWaitingRoom()
            firebaseService.deleteRoom(roomId)
        }
    }
}
