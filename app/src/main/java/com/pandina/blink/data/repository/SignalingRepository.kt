package com.pandina.blink.data.remote

import com.pandina.blink.data.model.SignalingData
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.UUID

class SignalingRepository() {

    val userId = UUID.randomUUID().toString()
    private val firebaseService: FirebaseService = FirebaseService(userId)

    /**
     * Maneja la lógica de señalización para obtener la descripción remota.
     * Crea una sala si no hay ninguna disponible o se conecta a una existente.
     *
     * @param sdp La descripción SDP local (oferta o respuesta).
     * @return Un flujo (`Flow`) que emite un `SignalingData` con la descripción remota.
     */
    fun getRemoteDescription(sdp: String): Flow<SignalingData> = callbackFlow {
        try {
            // Obtener o crear la sala
            val roomId = firebaseService.getAndRemoveWaitingRoom()?.let { existingRoomId ->
                // Si hay una sala disponible, conectarse a ella y obtener el offerSDP
                val offerSdp = firebaseService.connectToRoom(existingRoomId, sdp)
                offerSdp?.let { offerSdp ->
                    // Emitir el offerSDP inmediatamente
                    trySend(SignalingData(type = "remoteDescription", sdp = offerSdp))
                    return@callbackFlow
                }
                existingRoomId
            } ?: run {
                // Si no hay una sala, crear una nueva
                firebaseService.createWaitingRoom(sdp)
            }

            // Configurar la escucha solo si no se obtuvo un offerSDP
            val listener =
                firebaseService.listenForAnswer(roomId = roomId, onAnswerReceived = { remoteSdp ->
                    trySend(SignalingData(type = "remoteDescription", sdp = remoteSdp))
                })

            awaitClose { listener.remove() }
        } catch (e: Exception) {
            close(e)
        }
    }

}
