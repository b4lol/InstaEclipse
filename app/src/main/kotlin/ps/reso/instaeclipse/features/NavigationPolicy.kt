package ps.reso.instaeclipse.features

/** Pure ordering rules; unknown host tabs are preserved and Home/Profile remain reachable. */
object NavigationPolicy {
    private val known = setOf("FEED", "SEARCH", "CLIPS", "DIRECT", "PROFILE", "NEWS", "SHARE")

    @JvmStatic
    fun normalizeOrder(value: String?): List<String> = value.orEmpty().split(',')
        .map { it.trim().uppercase(java.util.Locale.ROOT) }.filter { it in known }.distinct()

    @JvmStatic
    fun arrange(available: List<String>, hidden: Set<String>, order: String?): List<String> {
        val visible = available.filter { it == "FEED" || it == "PROFILE" || it !in hidden }
        val requested = normalizeOrder(order).filter { it in visible }
        return requested + visible.filter { it !in requested }
    }
}
