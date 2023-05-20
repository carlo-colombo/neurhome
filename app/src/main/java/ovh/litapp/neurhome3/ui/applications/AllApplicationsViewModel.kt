package ovh.litapp.neurhome3.ui.applications

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ovh.litapp.neurhome3.data.Application
import ovh.litapp.neurhome3.data.NeurhomeRepository
import ovh.litapp.neurhome3.ui.NeurhomeViewModel

class ImportingDB(context: Context, name: String?) :
    SQLiteOpenHelper(context, name, null, 5) {
    override fun onCreate(db: SQLiteDatabase?) {
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
    }

}

class AllApplicationsViewModel(
    private val neurhomeRepository: NeurhomeRepository,
    packageManager: PackageManager,
    startActivity: (Intent) -> Unit,
) : NeurhomeViewModel(neurhomeRepository, packageManager, startActivity) {

    val uiState: StateFlow<UiState> = neurhomeRepository.apps.map { UiState(it) }.stateIn(
        viewModelScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = UiState()
    )

    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    fun import(u: Uri?) {
        coroutineScope.launch(Dispatchers.IO) {
            neurhomeRepository.insertFromDB(u)
        }
    }
}

data class UiState(
    val allApps: List<Application> = listOf(),
)
