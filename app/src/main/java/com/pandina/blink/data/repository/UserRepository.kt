package com.pandina.blink.data.repository

class UserRepository {
    fun getConnectedUsers(): Int {
        // Lógica real o simulada para obtener usuarios conectados
        return (1..1000).random() // Simulación con número aleatorio
    }
}