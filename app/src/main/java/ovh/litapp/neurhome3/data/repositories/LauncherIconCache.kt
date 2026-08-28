package ovh.litapp.neurhome3.data.repositories

internal data class LauncherIconKey(
    val packageName: String,
    val profile: Int,
    val componentName: String,
)

/** A small LRU cache so launcher refreshes do not retain every installed icon. */
internal class LauncherIconCache<T>(private val maxSize: Int) {
    private val entries = object : LinkedHashMap<LauncherIconKey, T>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<LauncherIconKey, T>?): Boolean =
            size > maxSize
    }

    @Synchronized
    fun getOrLoad(key: LauncherIconKey, loader: () -> T): T =
        entries[key] ?: loader().also { entries[key] = it }

    @Synchronized
    fun invalidatePackage(packageName: String, profile: Int) {
        entries.keys.removeAll { it.packageName == packageName && it.profile == profile }
    }
}
