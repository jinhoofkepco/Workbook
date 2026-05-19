package com.mathworkbook.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.mathworkbook.app.core.AppContainer
import com.mathworkbook.app.ui.MathWorkbookApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = AppContainer(applicationContext)
        setContent {
            MathWorkbookApp(container)
        }
    }
}
