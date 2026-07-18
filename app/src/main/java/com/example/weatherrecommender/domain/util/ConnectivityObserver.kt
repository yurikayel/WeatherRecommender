package com.example.weatherrecommender.domain.util

import kotlinx.coroutines.flow.Flow

/**
 * Represents the current state of network connectivity.
 */
enum class ConnectivityStatus {
    Available, Unavailable, Losing, Lost
}

/**
 * Observes the network connectivity state and emits updates.
 * Domain layer interface abstracting Android network callbacks.
 */
interface ConnectivityObserver {
    fun observe(): Flow<ConnectivityStatus>
}
