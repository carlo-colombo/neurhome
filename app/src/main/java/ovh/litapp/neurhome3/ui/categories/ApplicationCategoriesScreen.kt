package ovh.litapp.neurhome3.ui.categories

import androidx.compose.runtime.Composable

@Composable
fun ApplicationCategoriesScreen() {
    CategoryPager(includeHome = false, homeContent = { _ -> })
}
