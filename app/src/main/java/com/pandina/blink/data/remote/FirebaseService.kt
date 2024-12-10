package com.pandina.blink.data.remote

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FirebaseService(val userId: String) {
    private val db = FirebaseFirestore.getInstance()

    companion object {
        private const val COLLECTION_ROOMS = "rooms"
        private const val COLLECTION_WAITING_ROOM = "waitingRoom"
    }

    // Obtener el id de una sala en espera
    suspend fun getAndRemoveWaitingRoom(): String? {
        return db.runTransaction { transaction ->
            // Referencia al documento único de "waitingRoom"
            val docRef = db.collection(COLLECTION_WAITING_ROOM).document("current")

            // Leer el documento
            val snapshot = transaction.get(docRef)
            val roomId = snapshot.getString("roomId")

            if (roomId != null) {
                // Eliminar el documento si existe
                transaction.delete(docRef)
            }

            // Devolver el roomId (o null si no había waitingRoom)
            roomId
        }.await()
    }

    // Crear una sala con la oferta del propietario
    suspend fun createWaitingRoom(offerSdp: String): String {
        return try {
            val room = mapOf(
                "user1" to userId, "offer" to mapOf(
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

    // Tomar una sala vacía
    suspend fun connectToRoom(roomId: String, answerSdp: String): String? {
        if (roomId.isEmpty() || answerSdp.isEmpty()) {
            throw IllegalArgumentException("Room ID and Answer SDP cannot be empty")
        }
        return try {
            // Obtener el estado actual de la sala
            val roomSnapshot = db.collection(COLLECTION_ROOMS).document(roomId).get().await()
            val offerSdp = roomSnapshot.get("offer.sdp") as? String

            if (offerSdp.isNullOrEmpty()) {
                throw IllegalStateException("No offer SDP found in the room")
            }

            // Actualizar la sala con el answer SDP
            val answer = mapOf(
                "sdp" to answerSdp, "responderId" to userId
            )
            db.collection(COLLECTION_ROOMS).document(roomId).update(
                "user2", userId, "answer", answer
            ).await()

            // Eliminar la sala de waitingRoom
            db.collection(COLLECTION_WAITING_ROOM).document("current").delete().await()

            // Devolver el offer SDP encontrado
            offerSdp
        } catch (e: Exception) {
            Log.e("FirebaseService", "Error connecting to room: ${e.message}")
            null
        }
    }


    // Escuchar por una respuesta en una sala
    fun listenForAnswer(
        roomId: String, onAnswerReceived: (String) -> Unit
    ): ListenerRegistration {
        var listener: ListenerRegistration? = null
        listener =
            db.collection(COLLECTION_ROOMS).document(roomId).addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener

                val answerSdp = snapshot.get("answer.sdp") as? String
                if (answerSdp != null) {
                    // Llamar al callback cuando se recibe la respuesta
                    onAnswerReceived(answerSdp)

                    listener?.remove()

                    // Eliminar la sala
                    db.collection(COLLECTION_ROOMS).document(roomId).delete()
                }
            }
        return listener
    }
}
