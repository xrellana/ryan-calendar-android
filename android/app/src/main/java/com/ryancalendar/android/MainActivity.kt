package com.ryancalendar.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.ryancalendar.android.ui.AppViewModel
import com.ryancalendar.android.ui.RyanCalendarRoot
import com.ryancalendar.android.ui.theme.RyanCalendarTheme

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels {
        val app = application as RyanCalendarApp
        AppViewModel.factory(app, app.container.repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RyanCalendarTheme {
                RyanCalendarRoot(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onAppResumed()
    }
}
