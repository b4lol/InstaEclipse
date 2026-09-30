package ps.reso.instaeclipse.features

/** Exact thread IDs only. Missing/unknown request shapes keep ghost protection in place. */
object ReadReceiptPolicy {
    private val idPattern = Regex("[0-9]{6,64}")

    @JvmStatic
    fun allows(saved: String?, threadId: String?): Boolean = threadId != null && idPattern.matches(threadId) &&
        saved.orEmpty().split(',').any { it == threadId }

    @JvmStatic
    fun setAllowed(saved: String?, threadId: String, allow: Boolean): String {
        require(idPattern.matches(threadId))
        val ids = saved.orEmpty().split(',').filter { idPattern.matches(it) }.toMutableSet()
        if (allow) ids.add(threadId) else ids.remove(threadId)
        require(ids.size <= 500)
        return ids.sorted().joinToString(",")
    }

    @JvmStatic
    fun threadFromPath(path: String?): String? {
        val parts = path.orEmpty().split('/')
        val at = parts.indexOf("threads")
        return if (at >= 0 && at + 1 < parts.size) parts[at + 1].takeIf { idPattern.matches(it) } else null
    }
}
