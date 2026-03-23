package me.arnabsaha.airpodscompanion.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * Factory for creating [AirPodsViewModel] instances.
 *
 * Required because the ViewModel needs an [Application] context
 * to bind to [me.arnabsaha.airpodscompanion.service.AirPodsService]
 * and access SharedPreferences.
 *
 * Usage in a ComponentActivity:
 * ```
 * val viewModel: AirPodsViewModel by viewModels {
 *     AirPodsViewModelFactory(application)
 * }
 * ```
 *
 * Usage in a Composable:
 * ```
 * val viewModel: AirPodsViewModel = viewModel(
 *     factory = AirPodsViewModelFactory(context.applicationContext as Application)
 * )
 * ```
 */
class AirPodsViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AirPodsViewModel::class.java)) {
            return AirPodsViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
