import io.github.cdimascio.dotenv.dotenv
import kotlinx.datetime.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class Member(
    val name: String?,
    val id: Int,
    @SerialName("stars")
    val starCount: Int,

    @SerialName("local_score")
    val localScore: Int,

    @SerialName("global_score")
    val globalScore: Int,

    @SerialName("last_star_ts")
    val lastStarTS: Long,

    @SerialName("completion_day_level")
    private val completion: Map<String, Map<String, Star>>
) {

    @Transient
    val stars = (1..25).map {
        completion["$it"]?.get("1") to completion["$it"]?.get("2")
    }

    @Transient
    val friendlyScore = (1..25).sumOf { day ->
        val star1 = completion["$day"]?.get("1")
        val star2 = completion["$day"]?.get("2")

        val daysToSolvePart1 = star1?.let {
            (LocalDateTime(dotenv()["YEAR"].toInt(), 12, day, 5, 0, 0,)
                .toInstant(TimeZone.UTC)
                .until(Instant.fromEpochSeconds(it.getStarTS), DateTimeUnit.HOUR) / 24).toDouble()
        }

        val daysToSolvePart2 = star2?.let {
            (LocalDateTime(dotenv()["YEAR"].toInt(), 12, day, 5, 0, 0,)
                .toInstant(TimeZone.UTC)
                .until(Instant.fromEpochSeconds(it.getStarTS), DateTimeUnit.HOUR) / 24).toDouble()
        }

        val part1Score = star1?.let {
            1.0 / (daysToSolvePart1!! + 1)
        } ?: 0.0

        val part2Score = star2?.let {
            1.0 / (daysToSolvePart2!! + 1)
        } ?: 0.0

        part1Score + part2Score
    }

    fun getScore(scoring: Leaderboard.SCORING): Double = when(scoring) {
        Leaderboard.SCORING.DAY_BASED -> friendlyScore
        Leaderboard.SCORING.OFFICIAL -> localScore.toDouble()
        Leaderboard.SCORING.STAR_COUNT -> starCount.toDouble()
    }

    fun getScoreFormatted(scoring: Leaderboard.SCORING): String = when(scoring) {
        Leaderboard.SCORING.DAY_BASED -> String.format("%.2f", friendlyScore)
        Leaderboard.SCORING.OFFICIAL -> "$localScore"
        Leaderboard.SCORING.STAR_COUNT -> "$starCount"
    }
}