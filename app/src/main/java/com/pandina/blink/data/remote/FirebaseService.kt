package com.pandina.blink.data.remote

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.pandina.blink.data.model.RoomAwnser
import com.pandina.blink.data.model.RoomOffer
import kotlinx.coroutines.tasks.await
import org.webrtc.SessionDescription
import java.util.UUID


class FirebaseService(private val userId: String) {
    private val db = FirebaseFirestore.getInstance()

    companion object {
        private const val COLLECTION_ROOMS = "rooms"
        private const val COLLECTION_WAITING_ROOM = "waitingRoom"
        private const val COLLECTION_ICE_CANDIDATE = "iceCandidates"
    }

    suspend fun getWaitingRoom(): String? {
        return db.collection(COLLECTION_WAITING_ROOM).document("current").get().await()
            .getString("roomId")
    }

    // Obtener el id de una sala en espera
    suspend fun getAndRemoveWaitingRoom(): String? {
        return try {
            val docRef = db.collection(COLLECTION_WAITING_ROOM).document("current")

            // Obtener el documento
            val snapshot = docRef.get().await()

            // Leer el roomId
            val roomId = snapshot.getString("roomId")

            // Eliminar el documento si existe
            if (roomId != null) {
                docRef.delete().await()
            }

            roomId
        } catch (e: Exception) {
            Log.e("FirebaseService", "Error getting and removing waiting room: ${e.message}")
            null
        }
    }

    suspend fun remoteWaitingRoom() {
        db.collection(COLLECTION_WAITING_ROOM).document("current").delete().await()
    }

    // Crear una sala con la oferta del propietario
    suspend fun createWaitingRoom(offerSdp: String): String {
        return try {
            val room = mapOf(
                "offer" to mapOf(
                    "sdp" to offerSdp, "ownerId" to userId
                ), "answer" to null
            )
            val roomId = UUID.randomUUID().toString()
            db.collection(COLLECTION_ROOMS).document(roomId).set(room).await()
            db.collection(COLLECTION_WAITING_ROOM).document("current")
                .set(mapOf("roomId" to roomId)).await()
            roomId
        } catch (e: Exception) {
            // Manejar el error según sea necesario
            throw e
        }
    }

    suspend fun connectToRoom(roomId: String, answerSdp: String): RoomOffer? {
        if (roomId.isEmpty() || answerSdp.isEmpty()) {
            throw IllegalArgumentException("Room ID and Answer SDP cannot be empty")
        }
        return try {
            // Obtener el estado actual de la sala
            val roomSnapshot = db.collection(COLLECTION_ROOMS).document(roomId).get().await()
            val offerSdp = roomSnapshot.get("offer.sdp") as? String
            val ownerId = roomSnapshot.get("offer.ownerId") as? String

            if (offerSdp.isNullOrEmpty() || ownerId.isNullOrEmpty()) {
                throw IllegalStateException("Offer SDP or Owner ID not found in the room")
            }

            // Actualizar la sala con el answer SDP
            val answer = mapOf(
                "sdp" to answerSdp,
                "responderId" to userId
            )
            db.collection(COLLECTION_ROOMS).document(roomId).update(
                "answer", answer
            ).await()

            // Devolver el offer SDP y el ownerId encontrado
            RoomOffer(offerSdp = offerSdp, ownerId = ownerId)
        } catch (e: Exception) {
            Log.e("FirebaseService", "Error connecting to room: ${e.message}")
            null
        }
    }


    suspend fun getOfferSdp(roomId: String): SessionDescription? {
        val offerSpd =
            db.collection(COLLECTION_ROOMS).document(roomId).get().await().getString("offer.sdp")
        offerSpd?.let { return SessionDescription(SessionDescription.Type.OFFER, it) }
        return null
    }


    // Escuchar por una respuesta en una sala
    fun listenForAnswer(
        roomId: String, onAnswerReceived: (RoomAwnser) -> Unit
    ): ListenerRegistration {
        var listener: ListenerRegistration? = null
        listener =
            db.collection(COLLECTION_ROOMS).document(roomId).addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener

                val answerSdp = snapshot.get("answer.sdp") as? String
                val ownerId = snapshot.get("answer.responderId") as? String
                if (answerSdp != null && ownerId != null) {
                    // Llamar al callback cuando se recibe la respuesta
                    onAnswerReceived(RoomAwnser(awnserSdp = answerSdp, responderId = ownerId))

                    // Eliminar la sala [COMENTAR PARA MANTENER LA SALA]
                    //db.collection(COLLECTION_ROOMS).document(roomId).delete()

                    listener?.remove()
                }
            }
        return listener
    }

    fun listenForIceCandidate(
        remoteUserId: String, onIceCandidateReceived: (String, String, Int) -> Unit
    ): ListenerRegistration {
        val processedCandidates =
            mutableSetOf<String>() // Conjunto para rastrear candidatos procesados

        return db.collection(COLLECTION_ICE_CANDIDATE).document(remoteUserId)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener

                // Iterar sobre los datos del documento
                val iceCandidates = snapshot.data ?: return@addSnapshotListener
                for ((key, value) in iceCandidates) {
                    if (value is Map<*, *>) {
                        // Generar una clave única para identificar el candidato
                        val candidateKey = "$remoteUserId-$key"

                        // Verificar si ya se ha procesado
                        if (processedCandidates.contains(candidateKey)) continue

                        // Extraer valores del subdocumento
                        val candidate = value["candidate"] as? String
                        val sdpMid = value["sdpMid"] as? String
                        val sdpMLineIndex = (value["sdpMLineIndex"] as? Number)?.toInt()

                        if (candidate != null && sdpMid != null && sdpMLineIndex != null) {
                            // Ejecutar la función con el ICE Candidate
                            onIceCandidateReceived(candidate, sdpMid, sdpMLineIndex)
                            // Marcar este candidato como procesado
                            processedCandidates.add(candidateKey)
                        } else {
                            println("Error: ICE Candidate incompleto en $value")
                        }
                    }
                }
            }
    }

    suspend fun addIceCandidate(
        userId: String, candidate: String, sdpMid: String, sdpMLineIndex: Int
    ) {
        try {
            // Crear un nuevo mapa con los datos del ICE Candidate
            val iceCandidateData = mapOf(
                "candidate" to candidate, "sdpMid" to sdpMid, "sdpMLineIndex" to sdpMLineIndex
            )

            // Generar una clave única para el nuevo candidato
            val newCandidateId = UUID.randomUUID().toString()

            // Añadir el nuevo candidato a la colección bajo el userId
            db.collection(COLLECTION_ICE_CANDIDATE).document(userId)
                .set(mapOf(newCandidateId to iceCandidateData), SetOptions.merge()).await()

            println("ICE Candidate agregado exitosamente para el usuario $userId")
        } catch (e: Exception) {
            println("Error al agregar el ICE Candidate: ${e.message}")
        }
    }

    suspend fun deleteRoom(roomId: String) {
        db.collection(COLLECTION_ROOMS).document(roomId).delete().await()
    }

    suspend fun deleteIceCandidates() {
        db.collection(COLLECTION_ICE_CANDIDATE).document(userId).delete().await()
    }
}
