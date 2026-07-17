package com.example.weatherrecommender.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weatherrecommender.domain.model.AppError
import com.example.weatherrecommender.domain.model.Location
import com.example.weatherrecommender.domain.model.RankedActivity
import com.example.weatherrecommender.domain.model.WeatherForecast
import com.example.weatherrecommender.domain.repository.WeatherRepository
import com.example.weatherrecommender.domain.usecase.GetRankedActivitiesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.example.weatherrecommender.domain.util.ConnectivityObserver
import com.example.weatherrecommender.domain.util.ConnectivityStatus
import com.example.weatherrecommender.ui.util.UiText
import com.example.weatherrecommender.ui.util.asUiText

/**
 * Immutable state representing the entire Weather UI.
 *
 * @property query The current search query.
 * @property isSearching True if a search request is in-flight.
 * @property isLoadingForecast True if the forecast data is being fetched.
 * @property searchResults List of geocoding results matching the query.
 * @property selectedLocation The location currently selected by the user.
 * @property forecast The 7-day forecast for the selected location.
 * @property rankedActivities A sorted list of recommended activities based on the forecast.
 * @property error The main UI error, usually blocking or prominent.
 * @property syncError A background sync error for offline scenarios.
 */
data class WeatherUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val isLoadingForecast: Boolean = false,
    val searchResults: List<Location> = emptyList(),
    val selectedLocation: Location? = null,
    val forecast: WeatherForecast? = null,
    val rankedActivities: List<RankedActivity> = emptyList(),
    val error: UiText? = null,
    val syncError: UiText? = null // For showing offline toasts without breaking UI
)

/**
 * Orchestrates the UI logic and state for the main Weather Screen.
 * Employs unidirectional data flow and reacts to network connectivity events.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class WeatherViewModel @Inject constructor(
    private val repository: WeatherRepository,
    private val getRankedActivities: GetRankedActivitiesUseCase,
    private val connectivityObserver: ConnectivityObserver
) : ViewModel() {

    private val _uiState = MutableStateFlow(WeatherUiState())
    val uiState: StateFlow<WeatherUiState> = _uiState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = WeatherUiState()
    )

    private val searchQueryFlow = MutableStateFlow("")
    private var forecastJob: Job? = null
    
    private var currentConnectivityStatus: ConnectivityStatus = ConnectivityStatus.Available

    init {
        viewModelScope.launch {
            connectivityObserver.observe().collect { status ->
                currentConnectivityStatus = status
                if (status != ConnectivityStatus.Available && _uiState.value.isSearching) {
                    _uiState.update { 
                        it.copy(
                            error = AppError.NetworkError.NoConnectivity.asUiText(), 
                            isSearching = false
                        ) 
                    }
                }
            }
        }

        viewModelScope.launch {
            searchQueryFlow
                .debounce(500L)
                .filter { it.length > 2 }
                .distinctUntilChanged()
                .onEach { _uiState.update { state -> state.copy(isSearching = true, error = null) } }
                .collectLatest { query ->
                    if (currentConnectivityStatus != ConnectivityStatus.Available) {
                        _uiState.update { 
                            it.copy(
                                error = AppError.NetworkError.NoConnectivity.asUiText(), 
                                isSearching = false
                            ) 
                        }
                        return@collectLatest
                    }
                    
                    repository.searchCity(query).fold(
                        onSuccess = { locations ->
                            _uiState.update { it.copy(searchResults = locations, isSearching = false) }
                        },
                        onError = { err ->
                            _uiState.update { it.copy(error = err.asUiText(), isSearching = false) }
                        }
                    )
                }
        }
    }

    fun onQueryChanged(query: String) {
        _uiState.update { it.copy(query = query, selectedLocation = null, forecast = null, error = null, syncError = null) }
        searchQueryFlow.value = query
        
        if (query.length <= 2) {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
        }
    }

    fun onLocationSelected(location: Location) {
        _uiState.update { 
            it.copy(
                selectedLocation = location, 
                searchResults = emptyList(), 
                isLoadingForecast = true, 
                error = null,
                syncError = null
            ) 
        }
        
        forecastJob?.cancel()
        forecastJob = viewModelScope.launch {
            // Offline-first SSOT: Collect from local database
            repository.getForecastFlow(location).collect { forecast ->
                if (forecast != null) {
                    val activities = getRankedActivities(forecast)
                    _uiState.update { 
                        it.copy(
                            forecast = forecast,
                            rankedActivities = activities,
                            isLoadingForecast = false
                        )
                    }
                }
            }
        }
        
        // Trigger a background refresh (Network -> DB)
        viewModelScope.launch {
            repository.refreshForecast(location).fold(
                onSuccess = {
                    // Success is handled by the Flow emission from SSOT
                },
                onError = { err ->
                    _uiState.update { state -> 
                        val mappedError = err.asUiText()
                        if (state.forecast == null) {
                            // Empty cache, show full error
                            state.copy(error = mappedError, isLoadingForecast = false)
                        } else {
                            // Cached data exists, just show a subtle sync error
                            state.copy(syncError = mappedError, isLoadingForecast = false)
                        }
                    }
                }
            )
        }
    }

    fun refresh() {
        val location = uiState.value.selectedLocation ?: return
        
        if (currentConnectivityStatus != ConnectivityStatus.Available) {
            _uiState.update { it.copy(syncError = AppError.NetworkError.NoConnectivity.asUiText()) }
            return
        }

        viewModelScope.launch {
            repository.refreshForecast(location).fold(
                onSuccess = {
                    _uiState.update { it.copy(syncError = null, error = null) }
                },
                onError = { err ->
                    _uiState.update { state -> 
                        state.copy(syncError = err.asUiText())
                    }
                }
            )
        }
    }
}
