package eu.kanade.tachiyomi.animesource.model

/**
 * macOS stub of Hoster.
 */
open class Hoster(
    val hosterUrl: String = "",
    val hosterName: String = "",
    val videoList: List<Video>? = null,
    val internalData: String = "",
) {
    @Transient
    @Volatile
    var status: State = State.IDLE

    enum class State {
        IDLE, LOADING, READY, ERROR,
    }

    companion object {
        const val NO_HOSTER_LIST = "no_hoster_list"

        fun List<Video>.toHosterList(): List<Hoster> {
            return listOf(
                Hoster(
                    hosterUrl = "",
                    hosterName = NO_HOSTER_LIST,
                    videoList = this,
                ),
            )
        }
    }
}
