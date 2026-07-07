package eu.kanade.tachiyomi.animesource.model

/**
 * macOS stub of Video and related types.
 * Simplified from the source-api Video to avoid Android Uri dependency.
 */
data class Track(val url: String, val lang: String)

enum class ChapterType {
    Opening, Ending, Recap, MixedOp, Other,
}

data class TimeStamp(
    val start: Double,
    val end: Double,
    val name: String,
    val type: ChapterType = ChapterType.Other,
)

open class Video(
    var videoUrl: String = "",
    val videoTitle: String = "",
    val resolution: Int? = null,
    val bitrate: Int? = null,
    val headers: okhttp3.Headers? = null,
    val preferred: Boolean = false,
    val subtitleTracks: List<Track> = emptyList(),
    val audioTracks: List<Track> = emptyList(),
    val timestamps: List<TimeStamp> = emptyList(),
    val internalData: String = "",
    val initialized: Boolean = false,
    val videoPageUrl: String = "",
) {

    @Transient
    @Volatile
    var status: State = State.QUEUE

    enum class State {
        QUEUE, LOAD_VIDEO, READY, ERROR,
    }
}
