package com.hodowo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hodowo.ui.theme.HoDoWoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HoDoWoTheme {
                val skeletonViewModel: SkeletonViewModel = viewModel()
                SkeletonScreen(skeletonViewModel)
            }
        }
    }
}
