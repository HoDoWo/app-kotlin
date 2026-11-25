package com.hodowo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import coil.Coil
import coil.ImageLoader
import com.hodowo.data.ArSupportProvider
import com.hodowo.ui.HoDoWoApp
import com.hodowo.ui.theme.HoDoWoTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Guide images are kept in Coil's disk cache even when the server says not to
        // cache them, so the Home lists and the chats can be looked at offline.
        Coil.setImageLoader(
            ImageLoader.Builder(applicationContext)
                .respectCacheHeaders(false)
                .build()
        )
        // Ask ARCore early (#239): its first answer can take a moment, and the
        // AR tab should already know whether this phone supports AR.
        lifecycleScope.launch { ArSupportProvider.get(applicationContext).refresh() }
        setContent {
            HoDoWoTheme {
                // Chat + camera flow (#38, #64).
                // SkeletonScreen (#49) stays in the code base but is no longer
                // the start screen: its backend client will be plugged into
                // ChatViewModel in a follow-up PR.
                HoDoWoApp()
            }
        }
    }
}