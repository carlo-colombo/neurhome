package ovh.litapp.neurhome3.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ovh.litapp.neurhome3.NeurhomeApplication
import ovh.litapp.neurhome3.ui.home.HomeViewModel

object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer {
            val inventoryApplication = inventoryApplication()
            HomeViewModel(
                inventoryApplication.repository,
                inventoryApplication.packageManager,
                inventoryApplication::startActivity
            )
        }
    }
}

/**
 * Extension function to queries for [Application] object and returns an instance of
 * [NeurhomeApplication].
 */
fun CreationExtras.inventoryApplication(): NeurhomeApplication =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NeurhomeApplication)