package com.example.weatherrecommender.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weatherrecommender.domain.model.AppError
import com.example.weatherrecommender.domain.model.Location
import com.example.weatherrecommender.domain.model.RankedActivity
import com.example.weatherrecommender.domain.model.TopPick
import com.example.weatherrecommender.domain.model.WeatherForecast
import com.example.weatherrecommender.domain.repository.WeatherRepository
import com.example.weatherrecommender.domain.usecase.GetRankedActivitiesUseCase
import com.example.weatherrecommender.domain.usecase.GetTopPicksUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import javax.inject.Inject
import com.example.weatherrecommender.domain.util.ConnectivityObserver
import com.example.weatherrecommender.domain.util.ConnectivityStatus
import com.example.weatherrecommender.ui.util.UiText
import com.example.weatherrecommender.ui.util.asUiText

/**
 * Immutable state representing the entire Weather UI.
 *
 * The screen has two modes derived from [selectedLocation]:
 *  - **Home** (`selectedLocation == null`): a search bar plus a feed of [topPicks].
 *  - **Detail** (`selectedLocation != null`): the forecast with a per-day selector; [rankedActivities]
 *    always reflects [selectedDayIndex].
 *
 * @property query The current search query.
 * @property isSearching True if a search request is in-flight.
 * @property searchResults List of geocoding results matching the query.
 * @property selectedLocation The location currently selected by the user, or null on the home screen.
 * @property forecast The 7-day forecast for the selected location.
 * @property isLoadingForecast True while the forecast for the selected location is loading.
 * @property selectedDayIndex Index of the day whose activities are currently shown.
 * @property rankedActivities Applicable activities for [selectedDayIndex], sorted by score.
 * @property topPicks Population-weighted featured suggestions shown on the home screen.
 * @property isLoadingTopPicks True while the home suggestions are loading.
 * @property error The main UI error, usually blocking or prominent.
 * @property syncError A background sync error for offline scenarios.
 */
data class WeatherUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val searchResults: List<Location> = emptyList(),
    val selectedLocation: Location? = null,
    val forecast: WeatherForecast? = null,
    val isLoadingForecast: Boolean = false,
    val selectedDayIndex: Int = 0,
    val rankedActivities: List<RankedActivity> = emptyList(),
    val topPicks: List<TopPick> = emptyList(),
    val isLoadingTopPicks: Boolean = false,
    val error: UiText? = null,
    val syncError: UiText? = null
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
    private val getTopPicks: GetTopPicksUseCase,
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
                .debounce(500.milliseconds)
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

        loadTopPicks()
    }

    /** Loads the population-weighted featured suggestions for the home screen. */
    fun loadTopPicks() {
        _uiState.update { it.copy(isLoadingTopPicks = true) }
        viewModelScope.launch {
            delay(TOP_PICKS_LOAD_DEFER_MS.milliseconds)
            val picks = getTopPicks()
            _uiState.update { it.copy(topPicks = picks, isLoadingTopPicks = false) }
        }
    }

    private companion object {
        const val TOP_PICKS_LOAD_DEFER_MS = 400
    }

    fun onQueryChanged(query: String) {
        _uiState.update { it.copy(query = query, error = null) }
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
                query = "",
                forecast = null,
                rankedActivities = emptyList(),
                selectedDayIndex = 0,
                isLoadingForecast = true,
                error = null,
                syncError = null
            )
        }

        forecastJob?.cancel()
        forecastJob = viewModelScope.launch {
            // Offline-first SSOT: collect from the local database.
            repository.getForecastFlow(location).collect { forecast ->
                if (forecast != null) {
                    _uiState.update { state ->
                        val dayIndex = state.selectedDayIndex.coerceIn(0, forecast.dailyForecasts.lastIndex)
                        state.copy(
                            forecast = forecast,
                            rankedActivities = getRankedActivities(forecast, dayIndex),
                            selectedDayIndex = dayIndex,
                            isLoadingForecast = false
                        )
                    }
                }
            }
        }

        // Trigger a background refresh (network -> DB).
        viewModelScope.launch {
            repository.refreshForecast(location).fold(
                onSuccess = { /* Handled by the SSOT flow emission. */ },
                onError = { err ->
                    _uiState.update { state ->
                        val mappedError = err.asUiText()
                        if (state.forecast == null) {
                            state.copy(error = mappedError, isLoadingForecast = false)
                        } else {
                            state.copy(syncError = mappedError, isLoadingForecast = false)
                        }
                    }
                }
            )
        }
    }

    /** Re-ranks activities for the selected day without any network work. */
    fun onDaySelected(dayIndex: Int) {
        val forecast = _uiState.value.forecast ?: return
        if (dayIndex !in forecast.dailyForecasts.indices) return
        _uiState.update {
            it.copy(
                selectedDayIndex = dayIndex,
                rankedActivities = getRankedActivities(forecast, dayIndex)
            )
        }
    }

    /** Returns from the detail view to the home screen. */
    fun onBack() {
        forecastJob?.cancel()
        _uiState.update {
            it.copy(
                selectedLocation = null,
                forecast = null,
                rankedActivities = emptyList(),
                selectedDayIndex = 0,
                query = "",
                searchResults = emptyList(),
                isLoadingForecast = false,
                error = null,
                syncError = null
            )
        }
    }

    fun refresh() {
        val location = _uiState.value.selectedLocation ?: return

        if (currentConnectivityStatus != ConnectivityStatus.Available) {
            _uiState.update { it.copy(syncError = AppError.NetworkError.NoConnectivity.asUiText()) }
            return
        }

        viewModelScope.launch {
            repository.refreshForecast(location).fold(
                onSuccess = { _uiState.update { it.copy(syncError = null, error = null) } },
                onError = { err -> _uiState.update { it.copy(syncError = err.asUiText()) } }
            )
        }
    }
}
