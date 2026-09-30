package ps.reso.instaeclipse.features

object CommentSearchPolicy {
    /** Search only the supplied snapshot, never fetch more comments or retain it globally. */
    @JvmStatic
    fun search(comments: List<String>, query: String?): List<String> {
        val term = query.orEmpty().trim()
        if (term.isEmpty()) return emptyList()
        return comments.asSequence().filter { it.contains(term, ignoreCase = true) }.distinct().take(100).toList()
    }
}
