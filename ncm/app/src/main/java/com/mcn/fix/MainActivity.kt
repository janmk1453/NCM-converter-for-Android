package com.mcn.fix

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mcn.fix.ui.navigation.MainPage
import com.mcn.fix.ui.theme.McnConverterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            McnConverterTheme {
                MainPage()
            }
        }
    }
}
