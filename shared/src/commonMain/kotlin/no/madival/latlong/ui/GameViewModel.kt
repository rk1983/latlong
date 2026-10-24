package no.madival.latlong.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import no.madival.latlong.data.CountryRepository
import no.madival.latlong.data.StreakStorage
import no.madival.latlong.data.WorldData
import no.madival.latlong.data.getStreakStorage
import no.madival.latlong.domain.ContinentFilter
import no.madival.latlong.domain.Question
import no.madival.latlong.domain.QuestionGenerator

enum class GameMode {
    MAP_PIN,
    FLAG_QUIZ
}

data class GameUiState(
    val isLoading: Boolean = true,
    val gameMode: GameMode = GameMode.MAP_PIN,
    val continentFilter: ContinentFilter = ContinentFilter.ALL,
    val cachedCountries: List<CachedCountry> = emptyList(),
    val currentQuestion: Question? = null,
    val selectedOptionIndex: Int? = null,
    val isRevealed: Boolean = false,
    val isCorrect: Boolean = false,
    val score: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val questionsAnswered: Int = 0,
    val cameraTarget: CameraTarget? = null
)

class GameViewModel(
    private val repository: CountryRepository = CountryRepository(),
    private val generator: QuestionGenerator = QuestionGenerator(),
    private val streakStorage: StreakStorage = getStreakStorage()
) : ViewModel() {

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private var worldData: WorldData? = null
    private val recentCountryCodes = mutableListOf<String>()
    private var viewportWidth = 0f
    private var viewportHeight = 0f

    init {
        loadData()
    }

    private fun getStorageKey(mode: GameMode, filter: ContinentFilter): String {
        return "${mode.name}_${filter.name}"
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val world = repository.getWorldData()
                worldData = world

                // Build Path geometries once on background thread
                val cached = withContext(Dispatchers.Default) {
                    world.countries.map { country ->
                        val paths = MapProjection.buildCountryPaths(country)
                        CachedCountry(country, paths)
                    }
                }

                val mode = _uiState.value.gameMode
                val filter = _uiState.value.continentFilter
                val initialBestStreak = streakStorage.getBestStreak(getStorageKey(mode, filter))

                val initialQuestion = generator.generateQuestion(
                    world = world,
                    recentCountryCodes = recentCountryCodes.toSet(),
                    requireFlag = (mode == GameMode.FLAG_QUIZ),
                    continentFilter = filter
                )
                recentCountryCodes.add(initialQuestion.targetCountry.code)

                val target = if (viewportWidth > 0f && viewportHeight > 0f) {
                    if (mode == GameMode.FLAG_QUIZ) {
                        MapProjection.getGlobalOverviewCamera(viewportWidth, viewportHeight)
                    } else {
                        MapProjection.calculateCameraFraming(
                            initialQuestion.coordinate,
                            viewportWidth,
                            viewportHeight
                        )
                    }
                } else null

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        cachedCountries = cached,
                        currentQuestion = initialQuestion,
                        cameraTarget = target,
                        bestStreak = initialBestStreak
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun setGameMode(mode: GameMode) {
        if (_uiState.value.gameMode == mode) return
        val world = worldData ?: return
        val filter = _uiState.value.continentFilter
        val savedBestStreak = streakStorage.getBestStreak(getStorageKey(mode, filter))

        val newQuestion = generator.generateQuestion(
            world = world,
            recentCountryCodes = recentCountryCodes.toSet(),
            requireFlag = (mode == GameMode.FLAG_QUIZ),
            continentFilter = filter
        )
        recentCountryCodes.add(newQuestion.targetCountry.code)

        val target = if (viewportWidth > 0f && viewportHeight > 0f) {
            if (mode == GameMode.FLAG_QUIZ) {
                MapProjection.getGlobalOverviewCamera(viewportWidth, viewportHeight)
            } else {
                MapProjection.calculateCameraFraming(newQuestion.coordinate, viewportWidth, viewportHeight)
            }
        } else null

        _uiState.update {
            it.copy(
                gameMode = mode,
                streak = 0,
                bestStreak = savedBestStreak,
                currentQuestion = newQuestion,
                selectedOptionIndex = null,
                isRevealed = false,
                isCorrect = false,
                cameraTarget = target
            )
        }
    }

    fun setContinentFilter(filter: ContinentFilter) {
        if (_uiState.value.continentFilter == filter) return
        val world = worldData ?: return
        val mode = _uiState.value.gameMode
        val savedBestStreak = streakStorage.getBestStreak(getStorageKey(mode, filter))

        val newQuestion = generator.generateQuestion(
            world = world,
            recentCountryCodes = recentCountryCodes.toSet(),
            requireFlag = (mode == GameMode.FLAG_QUIZ),
            continentFilter = filter
        )
        recentCountryCodes.add(newQuestion.targetCountry.code)

        val target = if (viewportWidth > 0f && viewportHeight > 0f) {
            if (mode == GameMode.FLAG_QUIZ) {
                MapProjection.getGlobalOverviewCamera(viewportWidth, viewportHeight)
            } else {
                MapProjection.calculateCameraFraming(newQuestion.coordinate, viewportWidth, viewportHeight)
            }
        } else null

        _uiState.update {
            it.copy(
                continentFilter = filter,
                streak = 0,
                bestStreak = savedBestStreak,
                currentQuestion = newQuestion,
                selectedOptionIndex = null,
                isRevealed = false,
                isCorrect = false,
                cameraTarget = target
            )
        }
    }

    fun onViewportSizeChanged(width: Float, height: Float) {
        if (width <= 0f || height <= 0f) return
        val wasUnset = viewportWidth <= 0f || viewportHeight <= 0f
        viewportWidth = width
        viewportHeight = height

        if (wasUnset) {
            val q = _uiState.value.currentQuestion
            if (q != null && _uiState.value.cameraTarget == null) {
                val target = if (_uiState.value.gameMode == GameMode.FLAG_QUIZ && !_uiState.value.isRevealed) {
                    MapProjection.getGlobalOverviewCamera(width, height)
                } else {
                    MapProjection.calculateCameraFraming(q.coordinate, width, height)
                }
                _uiState.update { it.copy(cameraTarget = target) }
            }
        }
    }

    fun onAnswerSelected(index: Int) {
        val currentState = _uiState.value
        val question = currentState.currentQuestion ?: return
        if (currentState.isRevealed) return

        val isCorrect = index == question.correctIndex
        val newStreak = if (isCorrect) currentState.streak + 1 else 0
        val pointsEarned = if (isCorrect) 10 + (currentState.streak * 2) else 0
        val newScore = currentState.score + pointsEarned

        var updatedBestStreak = currentState.bestStreak
        if (newStreak > updatedBestStreak) {
            updatedBestStreak = newStreak
            streakStorage.saveBestStreak(
                getStorageKey(currentState.gameMode, currentState.continentFilter),
                newStreak
            )
        }

        val newCameraTarget = if (currentState.gameMode == GameMode.FLAG_QUIZ && viewportWidth > 0f && viewportHeight > 0f) {
            MapProjection.calculateCameraFraming(question.coordinate, viewportWidth, viewportHeight)
        } else {
            currentState.cameraTarget
        }

        _uiState.update {
            it.copy(
                selectedOptionIndex = index,
                isRevealed = true,
                isCorrect = isCorrect,
                score = newScore,
                streak = newStreak,
                bestStreak = updatedBestStreak,
                questionsAnswered = it.questionsAnswered + 1,
                cameraTarget = newCameraTarget
            )
        }
    }

    fun nextQuestion() {
        val world = worldData ?: return
        if (recentCountryCodes.size > 50) {
            recentCountryCodes.removeAt(0)
        }

        val mode = _uiState.value.gameMode
        val filter = _uiState.value.continentFilter
        val nextQ = generator.generateQuestion(
            world = world,
            recentCountryCodes = recentCountryCodes.toSet(),
            requireFlag = (mode == GameMode.FLAG_QUIZ),
            continentFilter = filter
        )
        recentCountryCodes.add(nextQ.targetCountry.code)

        val target = if (viewportWidth > 0f && viewportHeight > 0f) {
            if (mode == GameMode.FLAG_QUIZ) {
                MapProjection.getGlobalOverviewCamera(viewportWidth, viewportHeight)
            } else {
                MapProjection.calculateCameraFraming(nextQ.coordinate, viewportWidth, viewportHeight)
            }
        } else null

        _uiState.update {
            it.copy(
                currentQuestion = nextQ,
                selectedOptionIndex = null,
                isRevealed = false,
                isCorrect = false,
                cameraTarget = target
            )
        }
    }

    fun resetCamera() {
        val q = _uiState.value.currentQuestion ?: return
        if (viewportWidth > 0f && viewportHeight > 0f) {
            val target = if (_uiState.value.gameMode == GameMode.FLAG_QUIZ && !_uiState.value.isRevealed) {
                MapProjection.getGlobalOverviewCamera(viewportWidth, viewportHeight)
            } else {
                MapProjection.calculateCameraFraming(q.coordinate, viewportWidth, viewportHeight)
            }
            _uiState.update { it.copy(cameraTarget = target) }
        }
    }
}
