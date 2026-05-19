package com.komga.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.komga.android.data.local.PreferencesDataStore
import com.komga.android.notification.NewChapterWorker
import com.komga.android.ui.navigation.KomgaNavGraph
import com.komga.android.ui.navigation.Screen
import com.komga.android.ui.theme.KomgaTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var preferencesDataStore: PreferencesDataStore

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Permission granted or denied - WorkManager is already scheduled either way;
            // on denied, no notifications will appear but no crash occurs.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { false }

        // Hilt field injection happens inside super.onCreate()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Now preferencesDataStore is safely injected
        val startDestination = runBlocking {
            if (preferencesDataStore.isLoggedIn().first()) Screen.Home.route
            else Screen.Login.route
        }

        // Request POST_NOTIFICATIONS permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Schedule background chapter-check worker (runs every 2 hours, persists across reboots)
        scheduleChapterCheckWorker()

        setContent {
            KomgaTheme {
                KomgaNavGraph(startDestination = startDestination)
            }
        }
    }

    private fun scheduleChapterCheckWorker() {
        val workRequest = PeriodicWorkRequestBuilder<NewChapterWorker>(2, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "new_chapter_check",
            ExistingPeriodicWorkPolicy.KEEP,  // Don't restart if already queued
            workRequest
        )
    }
}
