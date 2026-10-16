package ovh.litapp.neurhome3.data.repositories

internal data class LauncherIconKey(
    val packageName: String,
    val profile: Int,
    val componentName: String,
)

/** A small LRU cache so launcher refreshes do not retain every installed icon. */
internal class LauncherIconCache<T>(private val maxSize: Int) {
    private val lock = Any()
    private val entries = object : LinkedHashMap<LauncherIconKey, T>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<LauncherIconKey, T>?): Boolean =
            size > maxSize
    }

    fun getOrLoad(key: LauncherIconKey, loader: () -> T): T {
        synchronized(lock) { entries[key]?.let { return it } }
        val loaded = loader()
        synchronized(lock) { entries[key] = loaded }
        return loaded
    }

    fun invalidatePackage(packageName: String, profile: Int) {
        synchronized(lock) {
            entries.keys.removeAll { it.packageName == packageName && it.profile == profile }
        }
    }
}
