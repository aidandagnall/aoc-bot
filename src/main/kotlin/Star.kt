import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Star(
    @SerialName("get_star_ts")
    val getStarTS: Long,
    @SerialName("star_index")
    val starIndex: Int,
)