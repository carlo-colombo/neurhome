package ovh.litapp.neurhome3.ui.categories

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun ApplicationCategoriesScreen() {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(text = "Categories")
        UncategorizedScreen(modifier = Modifier.weight(1f))
    }
}
