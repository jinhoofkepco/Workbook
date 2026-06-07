package com.mathworkbook.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.mathworkbook.app.core.AppContainer
import com.mathworkbook.app.core.backup.BackupExporter
import com.mathworkbook.app.ui.MathWorkbookApp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val rehideNavigationRunnable = Runnable { hideNavigationBar() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        keepNavigationBarHiddenAfterAccidentalReveal()
        hideNavigationBar()
        val container = AppContainer(applicationContext)
        lifecycleScope.launch(Dispatchers.IO) {
            BackupExporter.runDailyBackupIfNeeded(
                context = applicationContext,
                appPreferences = container.appPreferences,
                database = container.database
            )
        }
        setContent {
            MathWorkbookApp(container)
        }
    }

    override fun onResume() {
        super.onResume()
        hideNavigationBar()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideNavigationBar()
    }

    private fun keepNavigationBarHiddenAfterAccidentalReveal() {
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { view, insets ->
            if (insets.isVisible(WindowInsetsCompat.Type.navigationBars())) {
                view.removeCallbacks(rehideNavigationRunnable)
                view.postDelayed(rehideNavigationRunnable, 450L)
            }
            insets
        }
    }

    private fun hideNavigationBar() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.navigationBars())
        }
    }
}
