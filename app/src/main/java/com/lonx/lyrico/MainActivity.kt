package com.lonx.lyrico

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.lonx.lyrico.di.appModule
import com.lonx.lyrico.ui.theme.LyricoTheme
import com.lonx.lyrico.viewmodel.SettingsViewModel
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class MainActivity : ComponentActivity() {

    private val settingsViewModel: SettingsViewModel by inject()

    private lateinit var openDirectoryLauncher: ActivityResultLauncher<Uri?>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        startKoin {
            androidLogger(Level.ERROR) // Log Koin errors
            androidContext(this@MainActivity)
            modules(appModule)
        }

        openDirectoryLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            uri?.let {
                // Persist access permissions for the URI
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                contentResolver.takePersistableUriPermission(it, takeFlags)

                settingsViewModel.addScannedFolder(it.toString())
            }
        }

        enableEdgeToEdge()
        setContent {
            LyricoTheme {
                LyricoApp(
                    launchDirectoryPicker = { openDirectoryLauncher.launch(null) }
                )
            }
        }
    }
}