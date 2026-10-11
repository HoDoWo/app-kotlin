package com.hodowo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.hodowo.ui.HoDoWoApp
import com.hodowo.ui.theme.HoDoWoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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