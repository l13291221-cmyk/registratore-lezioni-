package com.animefit

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.animefit.notify.Reminders
import com.animefit.ui.screens.CameraScreen
import com.animefit.ui.screens.CharacterDetailScreen
import com.animefit.ui.screens.CharactersScreen
import com.animefit.ui.screens.HistoryScreen
import com.animefit.ui.screens.HomeScreen
import com.animefit.ui.screens.OnboardingScreen
import com.animefit.ui.screens.ResultScreen
import com.animefit.ui.screens.SettingsScreen
import com.animefit.ui.screens.WorkoutScreen
import com.animefit.ui.theme.AnimeFitTheme
import com.animefit.ui.theme.Neon

class MainActivity : ComponentActivity() {
    /** Schermata richiesta da una notifica ("camera" o "workout"). */
    private val openRequest = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) openRequest.value = intent.getStringExtra(Reminders.EXTRA_OPEN)
        setContent {
            AnimeFitTheme {
                AppNav(viewModel(), openRequest)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openRequest.value = intent.getStringExtra(Reminders.EXTRA_OPEN)
    }
}

private object Routes {
    const val ONBOARDING = "onboarding"
    const val CHARACTERS = "characters?onboarding={onboarding}"
    const val CHARACTER = "character/{id}?onboarding={onboarding}"
    const val HOME = "home"
    const val WORKOUT = "workout"
    const val CAMERA = "camera"
    const val RESULT = "result/{id}"
    const val HISTORY = "history"
    const val SETTINGS = "settings"

    fun characters(onboarding: Boolean) = "characters?onboarding=$onboarding"
    fun character(id: String, onboarding: Boolean) = "character/$id?onboarding=$onboarding"
    fun result(id: String) = "result/$id"
}

/** Svuota la pila e apre la home. */
private fun NavHostController.goHome() {
    navigate(Routes.HOME) { popUpTo(graph.id) { inclusive = true } }
}

@Composable
fun AppNav(vm: AppViewModel, openRequest: MutableState<String?>) {
    val nav = rememberNavController()
    val state by vm.state.collectAsStateWithLifecycle()
    val start = remember {
        when {
            state.profile == null -> Routes.ONBOARDING
            !state.onboarded -> Routes.characters(true)
            else -> Routes.HOME
        }
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(state.onboarded) {
        if (state.onboarded && Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val request = openRequest.value
    LaunchedEffect(request, state.onboarded) {
        if (request != null && state.onboarded) {
            openRequest.value = null
            nav.goHome()
            nav.navigate(if (request == Reminders.OPEN_CAMERA) Routes.CAMERA else Routes.WORKOUT)
        }
    }

    NavHost(nav, startDestination = start, modifier = Modifier.fillMaxSize().background(Neon.Black)) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(vm) { nav.navigate(Routes.characters(true)) }
        }
        composable(
            Routes.CHARACTERS,
            arguments = listOf(navArgument("onboarding") { type = NavType.BoolType; defaultValue = false }),
        ) { entry ->
            val onboarding = entry.arguments?.getBoolean("onboarding") == true
            CharactersScreen(
                vm,
                onOpen = { nav.navigate(Routes.character(it, onboarding)) },
                onBack = if (nav.previousBackStackEntry != null) ({ nav.popBackStack() }) else null,
            )
        }
        composable(
            Routes.CHARACTER,
            arguments = listOf(
                navArgument("id") { type = NavType.StringType },
                navArgument("onboarding") { type = NavType.BoolType; defaultValue = false },
            ),
        ) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val onboarding = entry.arguments?.getBoolean("onboarding") == true
            CharacterDetailScreen(
                vm, id,
                onChosen = {
                    nav.goHome()
                    if (onboarding || vm.state.value.photos.isEmpty()) nav.navigate(Routes.CAMERA)
                },
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                vm,
                onWorkout = { nav.navigate(Routes.WORKOUT) },
                onCamera = { nav.navigate(Routes.CAMERA) },
                onResult = { nav.navigate(Routes.result(it)) },
                onCharacter = { nav.navigate(Routes.character(it, false)) },
                onCharacters = { nav.navigate(Routes.characters(false)) },
                onHistory = { nav.navigate(Routes.HISTORY) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.WORKOUT) {
            WorkoutScreen(vm) { nav.popBackStack() }
        }
        composable(Routes.CAMERA) {
            CameraScreen(
                vm,
                onDone = { id -> nav.navigate(Routes.result(id)) { popUpTo(Routes.CAMERA) { inclusive = true } } },
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.RESULT, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            ResultScreen(
                vm, entry.arguments?.getString("id").orEmpty(),
                onRetake = { nav.navigate(Routes.CAMERA) { popUpTo(Routes.RESULT) { inclusive = true } } },
                onHome = { nav.goHome() },
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.HISTORY) {
            HistoryScreen(
                vm,
                onOpen = { nav.navigate(Routes.result(it)) },
                onCamera = { nav.navigate(Routes.CAMERA) },
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                vm,
                onCharacters = { nav.navigate(Routes.characters(false)) },
                onWiped = { nav.navigate(Routes.ONBOARDING) { popUpTo(nav.graph.id) { inclusive = true } } },
                onBack = { nav.popBackStack() },
            )
        }
    }
}
