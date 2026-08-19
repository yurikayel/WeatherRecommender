package com.example.weatherrecommender.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weatherrecommender.data.preferences.FirstRunThemeSettler
import com.example.weatherrecommender.domain.location.DeviceLocationProvider
import com.example.weatherrecommender.domain.model.AppError
import com.example.weatherrecommender.domain.model.Location
import com.example.weatherrecommender.domain.model.RankedActivity
import com.example.weatherrecommender.domain.model.Result
import com.example.weatherrecommender.domain.model.TopPick
import com.example.weatherrecommender.domain.model.WeatherForecast
import com.example.weatherrecommender.domain.repository.WeatherRepository
import com.example.weatherrecommender.domain.usecase.GetRankedActivitiesUseCase
import com.example.weatherrecommender.domain.usecase.GetTopPicksUseCase
import com.example.weatherrecommender.domain.util.ConnectivityObserver
import com.example.weatherrecommender.domain.util.ConnectivityStatus
import com.example.weatherrecommender.ui.map.MapCameraPosition
import com.example.weatherrecommender.ui.map.MapHopProfile
import com.example.weatherrecommender.ui.util.UiText
import com.example.weatherrecommender.ui.util.asUiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/**
 * Immutable state representing the entire Weather UI.
 *
 * The screen has two modes derived from [destination]:
 *  - **Home** ([WeatherDestination.Home]): a search bar plus a feed of [topPicks].
 *  - **Detail** ([WeatherDestination.Detail]): the forecast with a per-day selector; [rankedActivities]
 *    always reflects [selectedDayIndex].
 *
 * The map is driven by [mapCamera] / [mapPin]; [WeatherScreenContent] keeps a single map
 * instance whose layout height tracks the sheet while the sheet body Crossfades home↔detail.
 *
 * @property query The current search query (updated immediately; the network path is debounced).
 * @property search Geocoding lane: idle, in-flight (optionally with previous hits), or results.
 * @property destination Explicit home vs detail navigation target.
 * @property selectedLocation The location shown in detail, derived from [destination].
 * @property forecast The 7-day forecast for the selected location.
 * @property forecastFetch Loading vs idle for the detail forecast (independent of [search]).
 * @property selectedDayIndex Index of the day whose activities are currently shown.
 * @property rankedActivities Applicable activities for [selectedDayIndex], sorted by score.
 * @property weekTopActivities Top-ranked activity per forecast day (#1 each day); stable until forecast changes.
 * @property topPicks Population-weighted featured suggestions shown on the home screen.
 * @property topPicksFetch Idle, initial skeleton, or pull-to-refresh over existing [topPicks].
 * @property recentHistory The 10 most recently viewed cities, newest first (empty when none).
 * @property mapCamera Camera target for the in-screen map.
 * @property mapPin Marker shown on the map (selected city or search preview).
 * @property mapTapFetch Loading while reverse-geocoding a map tap.
 * @property deviceLocation Reverse-geocoded city for the device GPS fix; null hides the home chip.
 * @property error The main UI error, usually blocking or prominent.
 * @property syncError A background sync error for offline scenarios.
 */
data class WeatherUiState(
    val query: String = "",
    val search: SearchUiState = SearchUiState.Idle,
    val destination: WeatherDestination = WeatherDestination.Home,
    val forecast: WeatherForecast? = null,
    val forecastFetch: FetchStatus = FetchStatus.Idle,
    val selectedDayIndex: Int = 0,
    val rankedActivities: List<RankedActivity> = emptyList(),
    val weekTopActivities: List<RankedActivity?> = emptyList(),
    val topPicks: List<TopPick> = emptyList(),
    val topPicksFetch: FetchStatus = FetchStatus.Idle,
    val recentHistory: List<Location> = emptyList(),
    val mapCamera: MapCameraPosition = MapCameraPosition.DEFAULT,
    val mapPin: Location? = null,
    val mapTapFetch: FetchStatus = FetchStatus.Idle,
    val deviceLocation: Location? = null,
    val error: UiText? = null,
    val syncError: UiText? = null
) {
    /** Convenience accessor for detail mode; null on [WeatherDestination.Home]. */
    val selectedLocation: Location?
        get() = (destination as? WeatherDestination.Detail)?.location

    val isSearching: Boolean get() = search is SearchUiState.Loading

    val searchResults: List<Location>
        get() = when (val current = search) {
            SearchUiState.Idle -> emptyList()
            is SearchUiState.Loading -> current.previousResults
            is SearchUiState.Results -> current.locations
        }

    val isLoadingForecast: Boolean get() = forecastFetch == FetchStatus.Loading

    val isLoadingTopPicks: Boolean get() = topPicksFetch == FetchStatus.Loading

    val isRefreshingTopPicks: Boolean get() = topPicksFetch == FetchStatus.Refreshing

    val isResolvingMapTap: Boolean get() = mapTapFetch == FetchStatus.Loading
}

/**
 * Orchestrates the UI logic and state for the main Weather Screen.
 * Employs unidirectional data flow and reacts to network connectivity events.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
// Map/GPS/history grew the event surface; no clean collaborator split without fragmenting UDF.
@Suppress("TooManyFunctions")
class WeatherViewModel @Inject constructor(
    private val repository: WeatherRepository,
    private val getRankedActivities: GetRankedActivitiesUseCase,
    private val getTopPicks: GetTopPicksUseCase,
    private val connectivityObserver: ConnectivityObserver,
    private val deviceLocationProvider: DeviceLocationProvider,
    private val firstRunThemeSettler: FirstRunThemeSettler
) : ViewModel() {

    private val _uiState = MutableStateFlow(WeatherUiState())

    /**
     * The single source of truth for the UI state.
     * Combines search results, home data, and detail forecast data into a reactive stream.
     */
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    private val searchQueryFlow = MutableStateFlow("")
    private var forecastJob: Job? = null
    private var contentRevealJob: Job? = null
    private var deviceLocationJob: Job? = null
    private var pendingDetailLocation: Location? = null
    private var bufferedForecast: WeatherForecast? = null
    private var bufferedError: UiText? = null

    private var currentConnectivityStatus: ConnectivityStatus = ConnectivityStatus.Available

    init {
        viewModelScope.launch {
            connectivityObserver.observe().collect { status ->
                currentConnectivityStatus = status
                if (status != ConnectivityStatus.Available && _uiState.value.isSearching) {
                    _uiState.update { it.withSearchSettled(AppError.NetworkError.NoConnectivity.asUiText()) }
                }
            }
        }

        viewModelScope.launch {
            searchQueryFlow
                .debounce(500.milliseconds)
                .filter { it.length > 2 }
                .distinctUntilChanged()
                .onEach { _uiState.update { state ->
                    state.copy(
                        search = SearchUiState.Loading(state.searchResults),
                        error = null
                    )
                } }
                .collectLatest { query ->
                    if (currentConnectivityStatus != ConnectivityStatus.Available) {
                        _uiState.update {
                            it.withSearchSettled(AppError.NetworkError.NoConnectivity.asUiText())
                        }
                        return@collectLatest
                    }

                    repository.searchCity(query).fold(
                        onSuccess = { locations ->
                            _uiState.update { state ->
                                val preview = locations.firstOrNull()
                                state.copy(
                                    search = SearchUiState.Results(locations),
                                    mapCamera = preview?.toMapCamera(
                                        MapCameraPosition.CITY_ZOOM,
                                        MapHopProfile.CACHED
                                    )
                                        ?: state.mapCamera,
                                    mapPin = preview ?: state.mapPin
                                )
                            }
                        },
                        onError = { err ->
                            _uiState.update { it.withSearchSettled(err.asUiText()) }
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
            state.copy(
                topPicksFetch = if (forceRefresh) FetchStatus.Refreshing else FetchStatus.Loading,
                error = if (forceRefresh) null else state.error
            )
        }
        viewModelScope.launch {
            if (!forceRefresh) {
                delay(TOP_PICKS_LOAD_DEFER_MS.milliseconds)
            }
            val picks = getTopPicks(forceRefresh = forceRefresh)
            _uiState.update {
                it.copy(
                    topPicks = picks,
                    topPicksFetch = FetchStatus.Idle
                )
            }
        }
    }

    private companion object {
        const val TOP_PICKS_LOAD_DEFER_MS = 400
        const val HISTORY_LIMIT = 10
    }

    /**
     * Called when the user types in the search bar.
     * Debounces the query and triggers geocoding searches automatically.
     */
    fun onQueryChanged(query: String) {
        _uiState.update { it.copy(query = query, error = null) }
        searchQueryFlow.value = query

        if (query.length <= 2) {
            _uiState.update { it.copy(search = SearchUiState.Idle) }
        }
    }

    /**
     * Starts fetch immediately, plants the pin, then picks a [MapHopProfile] from Room freshness
     * and flies the map. Sheet content waits [MapHopProfile.contentRevealMs] (500 ms before land
     * on a cache miss, 200 ms before land when weather is already fresh).
     */
    fun onLocationSelected(location: Location) {
        pendingDetailLocation = location
        bufferedForecast = null
        bufferedError = null

        _uiState.update {
            it.copy(
                search = SearchUiState.Idle,
                query = "",
                error = null,
                syncError = null,
                mapTapFetch = FetchStatus.Idle,
                mapPin = location
            )
        }

        forecastJob?.cancel()
        contentRevealJob?.cancel()
        // Collect + refresh are siblings under one Job so selecting another city cancels both.
        forecastJob = viewModelScope.launch {
            launch {
                repository.getForecastFlow(location).collect { forecast ->
                    if (forecast != null) {
                        if (pendingDetailLocation?.id == location.id &&
                            _uiState.value.selectedLocation?.id != location.id
                        ) {
                            bufferedForecast = forecast
                        } else if (_uiState.value.selectedLocation?.id == location.id) {
                            applyForecastToUi(forecast)
                        }
                    }
                }
            }
            launch {
                repository.markLocationViewed(location)
                repository.refreshForecast(location).fold(
                    onSuccess = { /* SSOT flow emission. */ },
                    onError = { err ->
                        val mappedError = err.asUiText()
                        if (_uiState.value.selectedLocation?.id == location.id) {
                            _uiState.update { state ->
                                if (state.forecast == null) {
                                    state.copy(error = mappedError, forecastFetch = FetchStatus.Idle)
                                } else {
                                    state.copy(syncError = mappedError, forecastFetch = FetchStatus.Idle)
                                }
                            }
                        } else if (pendingDetailLocation?.id == location.id) {
                            bufferedError = mappedError
                        }
                    }
                )
                repository.prefetchNearbyCities(location)
            }
        }

        contentRevealJob = viewModelScope.launch {
            val hop = if (repository.hasFreshForecast(location)) {
                MapHopProfile.CACHED
            } else {
                MapHopProfile.CACHE_MISS
            }
            _uiState.update {
                it.copy(mapCamera = location.toMapCamera(MapCameraPosition.DETAIL_ZOOM, hop))
            }
            delay(hop.contentRevealMs.milliseconds)
            revealDetailContent(location)
        }
    }

    private fun revealDetailContent(location: Location) {
        if (pendingDetailLocation?.id != location.id) return
        val ready = bufferedForecast?.takeIf { it.location.id == location.id || namesMatch(it.location, location) }
        val pendingError = bufferedError
        bufferedError = null
        _uiState.update { state ->
            state.copy(
                destination = WeatherDestination.Detail(location),
                forecast = ready,
                rankedActivities = ready?.let { getRankedActivities(it, 0) } ?: emptyList(),
                weekTopActivities = ready?.let { computeWeekTopActivities(it) } ?: emptyList(),
                selectedDayIndex = 0,
                forecastFetch = if (ready == null && pendingError == null) FetchStatus.Loading else FetchStatus.Idle,
                error = if (ready == null) pendingError else null,
                syncError = if (ready != null) pendingError else null
            )
        }
        ready?.let { applyForecastToUi(it) }
    }

    private fun applyForecastToUi(forecast: WeatherForecast) {
        _uiState.update { state ->
            val maxIndex = maxOf(0, forecast.dailyForecasts.lastIndex)
            val dayIndex = state.selectedDayIndex.coerceIn(0, maxIndex)
            state.copy(
                destination = WeatherDestination.Detail(forecast.location),
                forecast = forecast,
                rankedActivities = getRankedActivities(forecast, dayIndex),
                weekTopActivities = computeWeekTopActivities(forecast),
                selectedDayIndex = dayIndex,
                forecastFetch = FetchStatus.Idle
            )
        }
    }

    private fun namesMatch(a: Location, b: Location): Boolean =
        a.name.equals(b.name, ignoreCase = true) &&
            a.country.equals(b.country, ignoreCase = true)

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
                mapTapFetch = FetchStatus.Loading,
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
                            mapTapFetch = FetchStatus.Idle,
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
     * Returns from the detail view to the home screen and re-centers on the device location
     * when available (static London default otherwise).
     * Keeps [deviceLocation] so the current-location chip stays available.
     */
    fun onBack() {
        forecastJob?.cancel()
        contentRevealJob?.cancel()
        pendingDetailLocation = null
        bufferedForecast = null
        bufferedError = null
        _uiState.update {
            it.copy(
                destination = WeatherDestination.Home,
                forecast = null,
                rankedActivities = emptyList(),
                weekTopActivities = emptyList(),
                selectedDayIndex = 0,
                query = "",
                search = SearchUiState.Idle,
                forecastFetch = FetchStatus.Idle,
                error = null,
                syncError = null,
                mapCamera = it.deviceLocation?.toMapCamera(
                    MapCameraPosition.HOME_DEFAULT_ZOOM,
                    MapHopProfile.CACHED
                )
                    ?: MapCameraPosition.DEFAULT,
                mapPin = null,
                mapTapFetch = FetchStatus.Idle
            )
        }
    }

    /**
     * Called from the UI after the runtime location permission dialog.
     * When granted, reverse-geocodes the last-known fix for the home chip and map center —
     * does **not** open detail; the user opts in via [onCurrentLocationClick].
     * When denied, home stays on the static default framing and the chip remains hidden.
     */
    fun onLocationPermissionResult(granted: Boolean) {
        if (!granted) {
            viewModelScope.launch { firstRunThemeSettler.settle(coordinates = null) }
            return
        }
        resolveDeviceLocation()
    }

    /** Home header chip: quick-check weather for the reverse-geocoded device city. */
    fun onCurrentLocationClick() {
        val location = _uiState.value.deviceLocation ?: return
        onLocationSelected(location)
    }

    private fun resolveDeviceLocation() {
        deviceLocationJob?.cancel()
        deviceLocationJob = viewModelScope.launch {
            val coords = deviceLocationProvider.getLastKnownLocation()
            firstRunThemeSettler.settle(coords)
            if (coords == null) return@launch
            if (currentConnectivityStatus != ConnectivityStatus.Available) return@launch

            repository.reverseGeocode(coords.latitude, coords.longitude).fold(
                onSuccess = { location ->
                    _uiState.update { state ->
                        state.copy(
                            deviceLocation = location,
                            // Center the home map on the device city; detail keeps its own camera.
                            mapCamera = if (state.destination is WeatherDestination.Home) {
                                location.toMapCamera(
                                    MapCameraPosition.HOME_DEFAULT_ZOOM,
                                    MapHopProfile.CACHED
                                )
                            } else {
                                state.mapCamera
                            }
                        )
                    }
                },
                onError = {
                    // Keep the static default framing; chip stays hidden without a resolved city.
                }
            )
        }
    }

    /**
     * Refresh entry point used by home pull-to-refresh (bonus): force-refreshes top picks
     * (bypassing TTL). When a location is selected, refreshes that city's forecast into Room
     * (kept for callers/tests; the detail UI no longer exposes PTR so it won't fight sheet drag).
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
                repository.refreshForecast(location, force = true).fold(
                    onSuccess = { _uiState.update { it.copy(syncError = null, error = null) } },
                    onError = { err -> _uiState.update { it.copy(syncError = err.asUiText()) } }
                )
            }
        }
    }

    private fun computeWeekTopActivities(forecast: WeatherForecast): List<RankedActivity?> =
        forecast.dailyForecasts.indices.map { dayIndex ->
            getRankedActivities(forecast, dayIndex).firstOrNull()
        }
}

private fun WeatherUiState.withSearchSettled(error: UiText): WeatherUiState {
    val settled = when (val current = search) {
        is SearchUiState.Loading ->
            if (current.previousResults.isEmpty()) SearchUiState.Idle
            else SearchUiState.Results(current.previousResults)
        else -> current
    }
    return copy(search = settled, error = error)
}

private fun Location.toMapCamera(
    zoom: Double,
    hop: MapHopProfile = MapHopProfile.CACHE_MISS
) = MapCameraPosition(
    latitude = latitude,
    longitude = longitude,
    zoom = zoom,
    hop = hop
)
