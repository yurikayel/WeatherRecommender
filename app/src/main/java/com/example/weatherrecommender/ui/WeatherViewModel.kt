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
import com.example.weatherrecommender.domain.usecase.CountryCityCatalog
import com.example.weatherrecommender.domain.usecase.GetRankedActivitiesUseCase
import com.example.weatherrecommender.domain.usecase.GetTopPicksUseCase
import com.example.weatherrecommender.domain.util.ConnectivityObserver
import com.example.weatherrecommender.domain.util.ConnectivityStatus
import com.example.weatherrecommender.ui.map.MapCameraPosition
import com.example.weatherrecommender.ui.map.MapHopProfile
import com.example.weatherrecommender.ui.util.UiText
import com.example.weatherrecommender.ui.util.asUiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
import java.util.concurrent.atomic.AtomicInteger
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
 * @property forecastFetch Loading, idle, or failed for the detail forecast (independent of [search]).
 * @property selectedDayIndex Index of the day whose activities are currently shown.
 * @property rankedActivities Applicable activities for [selectedDayIndex], sorted by score.
 * @property topPicks Population-weighted featured suggestions shown on the home screen.
 * @property topPicksFetch Idle, initial skeleton, pull-to-refresh, or a Top Picks lane failure.
 * @property recentHistory The 10 most recently viewed cities, newest first (empty when none).
 * @property mapCamera Camera target for the in-screen map.
 * @property mapPin Marker shown on the map (selected city or search preview).
 * @property mapTapFetch Loading or failed while reverse-geocoding a map tap.
 * @property deviceLocation Reverse-geocoded city for the device GPS fix; null hides the home chip.
 */
data class WeatherUiState(
    val query: String = "",
    val search: SearchUiState = SearchUiState.Idle,
    val destination: WeatherDestination = WeatherDestination.Home,
    val forecast: WeatherForecast? = null,
    val forecastFetch: FetchStatus = FetchStatus.Idle,
    val selectedDayIndex: Int = 0,
    val rankedActivities: List<RankedActivity> = emptyList(),
    val topPicks: List<TopPick> = emptyList(),
    val topPicksFetch: FetchStatus = FetchStatus.Idle,
    val recentHistory: List<Location> = emptyList(),
    val mapCamera: MapCameraPosition = MapCameraPosition.DEFAULT,
    val mapPin: Location? = null,
    val mapTapFetch: FetchStatus = FetchStatus.Idle,
    val deviceLocation: Location? = null
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
            is SearchUiState.Failed -> current.previousResults
        }

    val searchError: UiText?
        get() = (search as? SearchUiState.Failed)?.error

    val isLoadingForecast: Boolean get() = forecastFetch.isLoading()

    val isLoadingTopPicks: Boolean get() = topPicksFetch.isLoading()

    val isRefreshingTopPicks: Boolean get() = topPicksFetch.isRefreshing()

    val isResolvingMapTap: Boolean get() = mapTapFetch.isLoading()

    /** Home-visible failures from search, map-tap, and Top Picks — never the forecast lane. */
    fun homeLaneErrors(): List<UiText> = listOfNotNull(
        searchError,
        mapTapFetch.errorOrNull(),
        topPicksFetch.errorOrNull()
    )
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
    private val firstRunThemeSettler: FirstRunThemeSettler,
    private val countryCityCatalog: CountryCityCatalog
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
    private var mapTapJob: Job? = null
    private var deviceLocationJob: Job? = null
    private var countryWarmJob: Job? = null
    private var countryWarmCode: String? = null
    private val countryWarmBudget = AtomicInteger(0)
    private var pendingDetailLocation: Location? = null
    private var bufferedForecast: WeatherForecast? = null
    private var bufferedError: UiText? = null

    private var currentConnectivityStatus: ConnectivityStatus = ConnectivityStatus.Available

    init {
        observeConnectivity()
        observeSearchQueries()
        loadTopPicks()
        observeHistory()
    }

    /** Settles in-flight search to an error when connectivity drops. */
    private fun observeConnectivity() {
        viewModelScope.launch {
            connectivityObserver.observe().collect { status ->
                currentConnectivityStatus = status
                if (status != ConnectivityStatus.Available && _uiState.value.isSearching) {
                    _uiState.update {
                        it.withSearchSettled(AppError.NetworkError.NoConnectivity.asUiText())
                    }
                }
            }
        }
    }

    /** Debounces the search box and geocodes queries longer than two characters. */
    private fun observeSearchQueries() {
        viewModelScope.launch {
            searchQueryFlow
                .debounce(500.milliseconds)
                .filter { it.length > 2 }
                .distinctUntilChanged()
                .onEach { markSearchLoading() }
                .collectLatest { query -> searchCities(query) }
        }
    }

    /** Shows the search lane as loading while keeping previous hits visible. */
    private fun markSearchLoading() {
        _uiState.update { state ->
            state.copy(search = SearchUiState.Loading(state.searchResults))
        }
    }

    /** Geocodes [query] or surfaces a connectivity error without hitting the network. */
    private suspend fun searchCities(query: String) {
        if (currentConnectivityStatus != ConnectivityStatus.Available) {
            _uiState.update {
                it.withSearchSettled(AppError.NetworkError.NoConnectivity.asUiText())
            }
            return
        }
        repository.searchCity(query).fold(
            onSuccess = ::applySearchResults,
            onError = { err -> _uiState.update { it.withSearchSettled(err.asUiText()) } }
        )
    }

    /** Publishes geocoding hits and previews the first result on the map. */
    private fun applySearchResults(locations: List<Location>) {
        _uiState.update { state ->
            val preview = locations.firstOrNull()
            state.copy(
                search = SearchUiState.Results(locations),
                mapCamera = preview?.toMapCamera(
                    MapCameraPosition.CITY_ZOOM,
                    MapHopProfile.CACHED
                ) ?: state.mapCamera,
                mapPin = preview ?: state.mapPin
            )
        }
    }

    /** Mirrors the 10 most recently viewed cities into [WeatherUiState.recentHistory]. */
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
                topPicksFetch = if (forceRefresh) FetchStatus.Refreshing else FetchStatus.Loading
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
        const val GPS_COUNTRY_WARM_BUDGET = 8
        const val SELECTION_COUNTRY_WARM_BUDGET = 2
    }

    /**
     * Called when the user types in the search bar.
     * Debounces the query and triggers geocoding searches automatically.
     */
    fun onQueryChanged(query: String) {
        _uiState.update { it.copy(query = query) }
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
        beginLocationHop(location)
        startForecastJob(location)
        startContentReveal(location)
    }

    /** Cancels a competing map-tap, plants the pin, and clears hop buffers for [location]. */
    private fun beginLocationHop(location: Location) {
        mapTapJob?.cancel()
        mapTapJob = null
        pendingDetailLocation = location
        bufferedForecast = null
        bufferedError = null
        _uiState.update {
            it.copy(
                search = SearchUiState.Idle,
                query = "",
                mapTapFetch = FetchStatus.Idle,
                mapPin = location
            )
        }
    }

    /** Starts Room collect and refresh as sibling jobs so a later city selection cancels both. */
    private fun startForecastJob(location: Location) {
        forecastJob?.cancel()
        contentRevealJob?.cancel()
        // Collect + refresh are siblings under one Job so selecting another city cancels both.
        forecastJob = viewModelScope.launch {
            launch { collectForecast(location) }
            launch { refreshAndPrefetch(location) }
        }
    }

    /** Applies Room forecast emissions to the UI or buffers them until the hop reveal. */
    private suspend fun collectForecast(location: Location) {
        repository.getForecastFlow(location).collect { forecast ->
            if (forecast != null) bufferOrApplyForecast(location, forecast)
        }
    }

    /** Holds [forecast] until reveal, or paints it immediately if detail is already showing this city. */
    private fun bufferOrApplyForecast(location: Location, forecast: WeatherForecast) {
        val pendingThisCity = pendingDetailLocation?.id == location.id
        val showingThisCity = _uiState.value.selectedLocation?.id == location.id
        when {
            pendingThisCity && !showingThisCity -> bufferedForecast = forecast
            showingThisCity -> applyForecastToUi(forecast)
        }
    }

    /** Marks history, refreshes Open-Meteo into Room, then warms nearby hubs. */
    private suspend fun refreshAndPrefetch(location: Location) {
        repository.markLocationViewed(location)
        repository.refreshForecast(location).fold(
            onSuccess = { /* SSOT flow emission. */ },
            onError = { err -> applyRefreshError(location, err.asUiText()) }
        )
        repository.prefetchNearbyCities(location)
        val code = _uiState.value.deviceLocation?.let { countryIsoFor(it) }
            ?: countryIsoFor(location)
        code?.let { addCountryWarmBudget(it, SELECTION_COUNTRY_WARM_BUDGET) }
    }

    /** Routes a refresh failure onto the forecast lane or the hop buffer. */
    private fun applyRefreshError(location: Location, mappedError: UiText) {
        if (_uiState.value.selectedLocation?.id == location.id) {
            _uiState.update { it.copy(forecastFetch = FetchStatus.Failed(mappedError)) }
            return
        }
        if (pendingDetailLocation?.id == location.id) {
            bufferedError = mappedError
        }
    }

    /** Flies the camera with the TTL hop profile, then swaps the sheet to detail. */
    private fun startContentReveal(location: Location) {
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

    /** Switches destination to detail and paints any forecast buffered during the hop. */
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
                selectedDayIndex = 0,
                forecastFetch = revealedForecastFetch(ready, pendingError)
            )
        }
        ready?.let { applyForecastToUi(it) }
    }

    /** Re-ranks activities for the current day index from a freshly arrived [forecast]. */
    private fun applyForecastToUi(forecast: WeatherForecast) {
        _uiState.update { state ->
            val maxIndex = maxOf(0, forecast.dailyForecasts.lastIndex)
            val dayIndex = state.selectedDayIndex.coerceIn(0, maxIndex)
            state.copy(
                destination = WeatherDestination.Detail(forecast.location),
                forecast = forecast,
                rankedActivities = getRankedActivities(forecast, dayIndex),
                selectedDayIndex = dayIndex,
                forecastFetch = state.forecastFetch.completeIfInFlight()
            )
        }
    }

    /** True when two locations share a case-insensitive name and country. */
    private fun namesMatch(a: Location, b: Location): Boolean =
        a.name.equals(b.name, ignoreCase = true) &&
            a.country.equals(b.country, ignoreCase = true)

    /**
     * Tap / long-press on the map: reverse-geocode then open the same detail flow as search.
     */
    fun onMapTapped(latitude: Double, longitude: Double) {
        if (currentConnectivityStatus != ConnectivityStatus.Available) {
            _uiState.update {
                it.copy(mapTapFetch = FetchStatus.Failed(AppError.NetworkError.NoConnectivity.asUiText()))
            }
            return
        }
        startMapTap(latitude, longitude)
    }

    /** Centers the camera on the tap and starts reverse-geocoding that point. */
    private fun startMapTap(latitude: Double, longitude: Double) {
        mapTapJob?.cancel()
        _uiState.update {
            it.copy(
                mapTapFetch = FetchStatus.Loading,
                mapCamera = MapCameraPosition(
                    latitude = latitude,
                    longitude = longitude,
                    zoom = MapCameraPosition.CITY_ZOOM
                )
            )
        }
        mapTapJob = viewModelScope.launch { resolveMapTap(latitude, longitude) }
    }

    /** Opens detail for the reverse-geocoded city, or shows the geocoding error. */
    private suspend fun resolveMapTap(latitude: Double, longitude: Double) {
        repository.reverseGeocode(latitude, longitude).fold(
            onSuccess = { location -> onLocationSelected(location) },
            onError = { err ->
                _uiState.update {
                    it.copy(mapTapFetch = FetchStatus.Failed(err.asUiText()))
                }
            }
        )
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
        cancelDetailJobs()
        _uiState.update { it.toHome() }
    }

    /** Cancels hop/forecast/map-tap jobs and clears buffers so home cannot flash stale detail. */
    private fun cancelDetailJobs() {
        forecastJob?.cancel()
        contentRevealJob?.cancel()
        mapTapJob?.cancel()
        mapTapJob = null
        pendingDetailLocation = null
        bufferedForecast = null
        bufferedError = null
    }

    /**
     * Called from the UI after the runtime location permission dialog.
     * When granted, reverse-geocodes the last-known fix for the home chip and map center —
     * does **not** open detail; the user opts in via [onCurrentLocationClick].
     * When denied, home stays on the static default framing and the chip remains hidden.
     */
    fun onLocationPermissionResult(granted: Boolean) {
        if (!granted) {
            viewModelScope.launch { firstRunThemeSettler.settle() }
            return
        }
        resolveDeviceLocation()
    }

    /** Home header chip: quick-check weather for the reverse-geocoded device city. */
    fun onCurrentLocationClick() {
        val location = _uiState.value.deviceLocation ?: return
        onLocationSelected(location)
    }

    /** Reverse-geocodes last-known GPS for the home chip and, on home, recenters the map. */
    private fun resolveDeviceLocation() {
        deviceLocationJob?.cancel()
        deviceLocationJob = viewModelScope.launch {
            val coords = deviceLocationProvider.getLastKnownLocation()
            firstRunThemeSettler.settle()
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
                    countryIsoFor(location)?.let { addCountryWarmBudget(it, GPS_COUNTRY_WARM_BUDGET) }
                },
                onError = {
                    // Keep the static default framing; chip stays hidden without a resolved city.
                }
            )
        }
    }

    /**
     * Adds [extra] country-warm slots for [countryCode] and starts the sequential warmer if idle.
     * Changing country resets the budget so a new GPS country does not mix queues.
     */
    private fun addCountryWarmBudget(countryCode: String, extra: Int) {
        val code = countryCode.trim().uppercase()
        if (code.isEmpty() || extra <= 0) return
        if (countryWarmCode != code) {
            countryWarmCode = code
            countryWarmBudget.set(0)
            countryWarmJob?.cancel()
            countryWarmJob = null
        }
        countryWarmBudget.addAndGet(extra)
        if (countryWarmJob?.isActive == true) return
        countryWarmJob = viewModelScope.launch { drainCountryWarm(code) }
    }

    /**
     * Drains [countryWarmBudget] via [WeatherRepository.prefetchCountryCities] until empty
     * or the catalog has nothing left to warm this session.
     */
    private suspend fun drainCountryWarm(code: String) {
        while (drainCountryWarmSlice(code)) {
            // Leftover budget from a concurrent selection nudge.
        }
    }

    /**
     * Runs one prefetch slice. Returns true when a concurrent selection nudge should drain now.
     * Unused slots from a partial pass are restored but not retried immediately (avoids 429 spin).
     */
    private suspend fun drainCountryWarmSlice(code: String): Boolean {
        val limit = countryWarmBudget.getAndSet(0)
        if (limit <= 0) return false
        val result = try {
            repository.prefetchCountryCities(code, limit)
        } catch (e: CancellationException) {
            countryWarmBudget.addAndGet(limit)
            throw e
        }
        val catalogDone = result.remaining <= 0
        val concurrent = countryWarmBudget.get()
        if (!catalogDone) {
            val unused = (limit - result.warmed).coerceAtLeast(0)
            if (unused > 0) countryWarmBudget.addAndGet(unused)
        }
        return !catalogDone && result.warmed > 0 && concurrent > 0
    }

    /**
     * ISO alpha-2 for [location]: stored code, else the catalog's country-name map (UK→GB).
     */
    private fun countryIsoFor(location: Location): String? {
        val stored = location.countryCode?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
        return stored ?: countryCityCatalog.isoForCountryName(location.country)
    }

    /**
     * Refresh entry point used by home pull-to-refresh (bonus): force-refreshes top picks
     * (bypassing TTL). When a location is selected, refreshes that city's forecast into Room
     * (kept for callers/tests; the detail UI no longer exposes PTR so it won't fight sheet drag).
     */
    fun refresh() {
        val location = _uiState.value.selectedLocation
        if (location == null) {
            refreshHome()
            return
        }
        refreshSelectedForecast(location)
    }

    /** Pull-to-refresh on home: force-reloads featured cities when online. */
    private fun refreshHome() {
        if (currentConnectivityStatus != ConnectivityStatus.Available) {
            _uiState.update {
                it.copy(topPicksFetch = FetchStatus.Failed(AppError.NetworkError.NoConnectivity.asUiText()))
            }
            return
        }
        loadTopPicks(forceRefresh = true)
    }

    /** Force-refreshes the selected city's forecast into Room for callers/tests. */
    private fun refreshSelectedForecast(location: Location) {
        if (currentConnectivityStatus != ConnectivityStatus.Available) {
            _uiState.update {
                it.copy(forecastFetch = FetchStatus.Failed(AppError.NetworkError.NoConnectivity.asUiText()))
            }
            return
        }
        viewModelScope.launch {
            repository.refreshForecast(location, force = true).fold(
                onSuccess = { _uiState.update { it.copy(forecastFetch = FetchStatus.Idle) } },
                onError = { err -> _uiState.update { it.copy(forecastFetch = FetchStatus.Failed(err.asUiText())) } }
            )
        }
    }
}

/** Clears detail fields and recenters the camera on the device city or the London default. */
private fun WeatherUiState.toHome(): WeatherUiState = copy(
    destination = WeatherDestination.Home,
    forecast = null,
    rankedActivities = emptyList(),
    selectedDayIndex = 0,
    query = "",
    search = SearchUiState.Idle,
    forecastFetch = FetchStatus.Idle,
    mapCamera = deviceLocation?.toMapCamera(
        MapCameraPosition.HOME_DEFAULT_ZOOM,
        MapHopProfile.CACHED
    ) ?: MapCameraPosition.DEFAULT,
    mapPin = null,
    mapTapFetch = FetchStatus.Idle
)

/** Attaches [error] to the search lane while keeping any previous hits visible. */
private fun WeatherUiState.withSearchSettled(error: UiText): WeatherUiState {
    return copy(search = SearchUiState.Failed(error, searchResults))
}

/** Forecast lane status at sheet reveal: failed, first-load, or already painted. */
private fun revealedForecastFetch(ready: WeatherForecast?, pendingError: UiText?): FetchStatus {
    if (pendingError != null) return FetchStatus.Failed(pendingError)
    return if (ready == null) FetchStatus.Loading else FetchStatus.Idle
}

/** Builds a camera target at this city's coordinates with the given zoom and hop profile. */
private fun Location.toMapCamera(
    zoom: Double,
    hop: MapHopProfile = MapHopProfile.CACHE_MISS
) = MapCameraPosition(
    latitude = latitude,
    longitude = longitude,
    zoom = zoom,
    hop = hop
)
