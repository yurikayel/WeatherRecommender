package com.example.weatherrecommender.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weatherrecommender.domain.location.DeviceLocationProvider
import com.example.weatherrecommender.domain.model.AppError
import com.example.weatherrecommender.domain.model.Location
import com.example.weatherrecommender.domain.model.RankedActivity
import com.example.weatherrecommender.domain.model.TopPick
import com.example.weatherrecommender.domain.model.WeatherForecast
import com.example.weatherrecommender.domain.repository.WeatherRepository
import com.example.weatherrecommender.domain.usecase.GetRankedActivitiesUseCase
import com.example.weatherrecommender.domain.usecase.GetTopPicksUseCase
import com.example.weatherrecommender.domain.usecase.WorldCapitals
import com.example.weatherrecommender.domain.util.ConnectivityObserver
import com.example.weatherrecommender.domain.util.ConnectivityStatus
import com.example.weatherrecommender.ui.map.MapCameraPosition
import com.example.weatherrecommender.ui.util.UiText
import com.example.weatherrecommender.ui.util.asUiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

/**
 * Immutable state representing the entire Weather UI.
 *
 * The screen has two modes derived from [selectedLocation]:
 *  - **Home** (`selectedLocation == null`): a search bar plus a feed of [topPicks].
 *  - **Detail** (`selectedLocation != null`): the forecast with a per-day selector; [rankedActivities]
 *    always reflects [selectedDayIndex].
 *
 * The map is driven by [mapCamera] / [mapPin]; each screen embeds it at the top of its scroll
 * content while this state keeps camera/pin continuous across home↔detail.
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
 * @property isLoadingTopPicks True while the home suggestions are loading (initial skeleton).
 * @property isRefreshingTopPicks True while pull-to-refresh is force-refreshing top picks.
 * @property recentHistory The 10 most recently viewed cities, newest first (empty when none).
 * @property mapCamera Camera target for the in-screen map.
 * @property mapPin Marker shown on the map (selected city or search preview).
 * @property isResolvingMapTap True while reverse-geocoding a map tap.
 * @property deviceLocation Reverse-geocoded city for the device GPS fix; null hides the home chip.
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
    val isRefreshingTopPicks: Boolean = false,
    val recentHistory: List<Location> = emptyList(),
    val mapCamera: MapCameraPosition = MapCameraPosition.DEFAULT,
    val mapPin: Location? = null,
    val isResolvingMapTap: Boolean = false,
    val deviceLocation: Location? = null,
    val error: UiText? = null,
    val syncError: UiText? = null
)

/**
 * Orchestrates the UI logic and state for the main Weather Screen.
 * Employs unidirectional data flow and reacts to network connectivity events.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
// Map/GPS/history grew the event surface; no clean collaborator split without fragmenting UDF.
@Suppress("TooManyFunctions")
class WeatherViewModel @Inject constructor(
    private val repository: WeatherRepository,
    private val getRankedActivities: GetRankedActivitiesUseCase,
    private val getTopPicks: GetTopPicksUseCase,
    private val connectivityObserver: ConnectivityObserver,
    private val worldCapitals: WorldCapitals,
    private val deviceLocationProvider: DeviceLocationProvider,
    private val random: Random
) : ViewModel() {

    private val initialHomeCamera = worldCapitals.random(random).toMapCamera(MapCameraPosition.HOME_DEFAULT_ZOOM)

    private val _uiState = MutableStateFlow(WeatherUiState(mapCamera = initialHomeCamera))
    val uiState: StateFlow<WeatherUiState> = _uiState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = WeatherUiState(mapCamera = initialHomeCamera)
    )

    private val searchQueryFlow = MutableStateFlow("")
    private var forecastJob: Job? = null
    private var deviceLocationJob: Job? = null

    /** Ensures first-launch GPS auto-select runs at most once per ViewModel lifetime. */
    private var hasAutoSelectedDeviceLocation = false

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
                            _uiState.update { state ->
                                val preview = locations.firstOrNull()
                                state.copy(
                                    searchResults = locations,
                                    isSearching = false,
                                    mapCamera = preview?.toMapCamera(MapCameraPosition.CITY_ZOOM)
                                        ?: state.mapCamera,
                                    mapPin = preview ?: state.mapPin
                                )
                            }
                        },
                        onError = { err ->
                            _uiState.update { it.copy(error = err.asUiText(), isSearching = false) }
                        }
                    )
                }
        }

        loadTopPicks()
        observeHistory()
    }

    private fun observeHistory() {
        viewModelScope.launch {
            repository.observeRecentLocations(HISTORY_LIMIT).collect { history ->
                _uiState.update { it.copy(recentHistory = history) }
            }
        }
    }

    /**
     * Loads the population-weighted featured suggestions for the home screen.
     * Pass [forceRefresh] = true (e.g. pull-to-refresh) to bypass the in-memory TTL cache.
     */
    fun loadTopPicks(forceRefresh: Boolean = false) {
        _uiState.update { state ->
            if (forceRefresh) {
                state.copy(isRefreshingTopPicks = true, error = null)
            } else {
                state.copy(isLoadingTopPicks = true)
            }
        }
        viewModelScope.launch {
            if (!forceRefresh) {
                delay(TOP_PICKS_LOAD_DEFER_MS.milliseconds)
            }
            val picks = getTopPicks(forceRefresh = forceRefresh)
            _uiState.update {
                it.copy(
                    topPicks = picks,
                    isLoadingTopPicks = false,
                    isRefreshingTopPicks = false
                )
            }
        }
    }

    private companion object {
        const val TOP_PICKS_LOAD_DEFER_MS = 400
        const val HISTORY_LIMIT = 10
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
                syncError = null,
                isResolvingMapTap = false,
                mapCamera = location.toMapCamera(MapCameraPosition.DETAIL_ZOOM),
                mapPin = location
            )
        }

        forecastJob?.cancel()
        forecastJob = viewModelScope.launch {
            // Offline-first SSOT: collect from the local database.
            repository.getForecastFlow(location).collect { forecast ->
                if (forecast != null) {
                    _uiState.update { state ->
                        val maxIndex = maxOf(0, forecast.dailyForecasts.lastIndex)
                        val dayIndex = state.selectedDayIndex.coerceIn(0, maxIndex)
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

        // Mark viewed before refresh so lastViewedAt is preserved across the Room REPLACE upsert.
        viewModelScope.launch {
            repository.markLocationViewed(location)
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

    /**
     * Tap / long-press on the map: reverse-geocode then open the same detail flow as search.
     */
    fun onMapTapped(latitude: Double, longitude: Double) {
        if (_uiState.value.isResolvingMapTap) return
        if (currentConnectivityStatus != ConnectivityStatus.Available) {
            _uiState.update { it.copy(error = AppError.NetworkError.NoConnectivity.asUiText()) }
            return
        }

        _uiState.update {
            it.copy(
                isResolvingMapTap = true,
                error = null,
                mapCamera = MapCameraPosition(
                    latitude = latitude,
                    longitude = longitude,
                    zoom = MapCameraPosition.CITY_ZOOM
                )
            )
        }

        viewModelScope.launch {
            repository.reverseGeocode(latitude, longitude).fold(
                onSuccess = { location ->
                    onLocationSelected(location)
                },
                onError = { err ->
                    _uiState.update {
                        it.copy(
                            isResolvingMapTap = false,
                            error = err.asUiText()
                        )
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

    /**
     * Returns from the detail view to the home screen and centers on a new random capital.
     * Keeps [deviceLocation] so the current-location chip stays available.
     */
    fun onBack() {
        forecastJob?.cancel()
        val capital = worldCapitals.random(random)
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
                syncError = null,
                mapCamera = capital.toMapCamera(MapCameraPosition.HOME_DEFAULT_ZOOM),
                mapPin = null,
                isResolvingMapTap = false
            )
        }
    }

    /**
     * Called from the UI after the runtime location permission dialog.
     * When granted, resolves the device city; on first success auto-opens its weather.
     * When denied, home stays on the random capital and the chip remains hidden.
     */
    fun onLocationPermissionResult(granted: Boolean) {
        if (!granted) return
        resolveDeviceLocation(autoSelect = !hasAutoSelectedDeviceLocation)
    }

    /** Home header chip: quick-check weather for the reverse-geocoded device city. */
    fun onCurrentLocationClick() {
        val location = _uiState.value.deviceLocation ?: return
        onLocationSelected(location)
    }

    private fun resolveDeviceLocation(autoSelect: Boolean) {
        deviceLocationJob?.cancel()
        deviceLocationJob = viewModelScope.launch {
            val coords = deviceLocationProvider.getLastKnownLocation() ?: return@launch
            if (currentConnectivityStatus != ConnectivityStatus.Available) return@launch

            repository.reverseGeocode(coords.latitude, coords.longitude).fold(
                onSuccess = { location ->
                    _uiState.update { it.copy(deviceLocation = location) }
                    if (autoSelect && !hasAutoSelectedDeviceLocation) {
                        hasAutoSelectedDeviceLocation = true
                        onLocationSelected(location)
                    }
                },
                onError = {
                    // Keep random capital framing; chip stays hidden without a resolved city.
                }
            )
        }
    }

    /**
     * Pull-to-refresh: on home, force-refreshes top picks (bypassing TTL);
     * on detail, refreshes the selected city's forecast into Room.
     */
    fun refresh() {
        val location = _uiState.value.selectedLocation
        if (location == null) {
            if (currentConnectivityStatus != ConnectivityStatus.Available) {
                _uiState.update { it.copy(error = AppError.NetworkError.NoConnectivity.asUiText()) }
            } else {
                loadTopPicks(forceRefresh = true)
            }
            return
        }

        if (currentConnectivityStatus != ConnectivityStatus.Available) {
            _uiState.update { it.copy(syncError = AppError.NetworkError.NoConnectivity.asUiText()) }
        } else {
            viewModelScope.launch {
                repository.refreshForecast(location).fold(
                    onSuccess = { _uiState.update { it.copy(syncError = null, error = null) } },
                    onError = { err -> _uiState.update { it.copy(syncError = err.asUiText()) } }
                )
            }
        }
    }
}

private fun Location.toMapCamera(zoom: Double) = MapCameraPosition(
    latitude = latitude,
    longitude = longitude,
    zoom = zoom
)
