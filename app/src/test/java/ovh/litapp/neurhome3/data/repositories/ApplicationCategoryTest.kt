package ovh.litapp.neurhome3.data.repositories

import org.junit.Assert.assertEquals
import org.junit.Test
import ovh.litapp.neurhome3.data.Application

class ApplicationCategoryTest {
    private fun app(label: String, vararg tags: String) =
        Application(label = label, packageName = label, icon = null, tags = tags.toList())

    @Test
    fun filtersAndOrdersTaggedApplicationsIgnoringCase() {
        val apps = listOf(app("zulu", "Work"), app("alpha", "Work"), app("other", "Play"))

        assertEquals(listOf("alpha", "zulu"), filterApplicationsForTag(apps, "Work").map { it.label })
    }

    @Test
    fun applicationsAssignedToMultipleTagsAppearInEachTag() {
        val shared = app("shared", "Work", "Play")
        val apps = listOf(shared, app("work", "Work"), app("play", "Play"))

        assertEquals(listOf("shared", "work"), filterApplicationsForTag(apps, "Work").map { it.label })
        assertEquals(listOf("play", "shared"), filterApplicationsForTag(apps, "Play").map { it.label })
    }

    @Test
    fun uncategorizedContainsOnlyApplicationsWithoutTags() {
        val apps = listOf(app("tagged", "Work"), app("free"))

        assertEquals(listOf("free"), filterApplicationsForTag(apps, null).map { it.label })
    }
}
