package com.swadeshiscanner.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.swadeshiscanner.app.ui.MainScreen
import com.swadeshiscanner.app.ui.theme.SwadeshiScannerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()

        lifecycleScope.launch(Dispatchers.IO) {
            cleanAppCache()
        }

        setContent {
            SwadeshiScannerTheme {
                MainScreen(
                    onScanClick = {
                        val intent = Intent(this, CameraActivity::class.java)
                        intent.putExtra("existing_doc_id", -1L)
                        startActivity(intent)
                    }
                )
            }
        }
    }

    private fun cleanAppCache() {
        try {
            val cacheDir = applicationContext.cacheDir
            if (cacheDir.exists()) {
                cacheDir.deleteRecursively()
                cacheDir.mkdirs()
            }
            
            // Optionally clear Glide disk cache
            // Glide.get(applicationContext).clearDiskCache()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
