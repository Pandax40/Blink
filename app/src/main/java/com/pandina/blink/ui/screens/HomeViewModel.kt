package com.pandina.blink.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pandina.blink.data.repository.UserRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(private val userRepository: UserRepository) : ViewModel() {

    private val _connectedUsers = MutableStateFlow(0) // Número de usuarios conectados
    val connectedUsers: StateFlow<Int> = _connectedUsers.asStateFlow()

    init {
        fetchConnectedUsers()
    }

    // Simula la lógica para obtener usuarios conectados
    private fun fetchConnectedUsers() {
        viewModelScope.launch {
            while (true) { // Actualización periódica simulada
                _connectedUsers.value = userRepository.getConnectedUsers() // Lógica del repositorio
                delay(5000) // Actualiza cada 5 segundos
            }
        }
    }
}
