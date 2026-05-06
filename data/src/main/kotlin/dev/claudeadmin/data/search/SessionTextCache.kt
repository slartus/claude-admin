package dev.claudeadmin.data.search

class SessionTextCache(private val capacity: Int = DEFAULT_CAPACITY) {

    data class Entry(val mtime: Long, val text: String)

    private val map = object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean =
            size > capacity
    }

    @Synchronized
    fun get(id: String, mtime: Long): String? {
        val entry = map[id] ?: return null
        return if (entry.mtime == mtime) entry.text else null
    }

    @Synchronized
    fun put(id: String, mtime: Long, text: String) {
        map[id] = Entry(mtime, text)
    }

    @Synchronized
    fun retainOnly(aliveIds: Set<String>) {
        map.keys.retainAll(aliveIds)
    }

    private companion object {
        const val DEFAULT_CAPACITY = 500
    }
}
