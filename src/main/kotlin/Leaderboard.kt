import gui.ava.html.image.generator.HtmlImageGenerator
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import javax.imageio.ImageIO

@Serializable
data class Leaderboard(
    val event: String,
    @SerialName("owner_id")
    val ownerId: Int,
    val members: Map<String, Member>
)
{
    companion object {

        const val DEFAULT_LEADERBOARD_SIZE = 20

        // standard HTML header, load AoC CSS, and add column labels
        const val HTML_HEADERS = """<html style="width: 100%; height: 100%; margin: 0px; padding: 0px; overflow-x: hidden;"> <head> <link href="https://adventofcode.com/static/style.css?30" rel="stylesheet" type="text/css"> <body style="width: 100%; height: 100%; margin: 0px; padding: 0px; overflow-x: hidden;"> <main> <article> <table> <thead></thead><tbody> <tr><div class="privboard-row"><td/><td/><span class="privboard-days"> <td><a href="/2021/day/1"><br>1</a></td> <td><a href="/2021/day/1"><br>2</a></td> <td><a href="/2021/day/1"><br>3</a></td> <td><a href="/2021/day/1"><br>4</a></td> <td><a href="/2021/day/1"><br>5</a></td> <td><a href="/2021/day/1"><br>6</a></td> <td><a href="/2021/day/1"><br>7</a></td> <td><a href="/2021/day/1"><br>8</a></td> <td><a href="/2021/day/1"><br>9</a></td> <td><a href="/2021/day/1">1 <br> 0</a></td> <td><a href="/2021/day/1">1 <br> 1</a></td> <td><a href="/2021/day/1">1 <br> 2</a></td> <td><a href="/2021/day/1">1 <br> 3</a></td> <td><a href="/2021/day/1">1 <br> 4</a></td> <td><a href="/2021/day/1">1 <br> 5</a></td> <td><a href="/2021/day/1">1 <br> 6</a></td> <td><a href="/2021/day/1">1 <br> 7</a></td> <td><a href="/2021/day/1">1 <br> 8</a></td> <td><a href="/2021/day/1">1 <br> 9</a></td> <td><a href="/2021/day/1">2 <br> 0</a></td> <td><a href="/2021/day/1">2 <br> 1</a></td> <td><a href="/2021/day/1">2 <br> 2</a></td> <td><a href="/2021/day/1">2 <br> 3</a></td> <td><a href="/2021/day/1">2 <br> 4</a></td> <td><a href="/2021/day/1">2 <br> 5</a></td><td/></tr> """
        const val HTML_FOOTERS = """</tbody></table></article></main></body></html>"""

        fun fromFile(path: String = "leaderboard.json"): Leaderboard? {
            return if (File(path).exists()) {
                Json.decodeFromString<Leaderboard>(
                    File(path)
                    .inputStream().bufferedReader().use { it.readText() })
            } else null
        }
    }

    fun createImage(path: String = "leaderboard.png", count: Int? = DEFAULT_LEADERBOARD_SIZE, scoring: SCORING = SCORING.OFFICIAL) {
        val generator = HtmlImageGenerator()

        // create the rows for each user
        val userRows = members.values.asSequence()
            // ignore users with no score
            .filter { it.getScore(scoring) > 0 }
            // highest score first
            .sortedByDescending{ it.getScore(scoring) }
            // take only the requested number of members
            .take(count ?: DEFAULT_LEADERBOARD_SIZE)
            .mapIndexed { it, member ->
                // position and score
                val positionInfo = """<tr><div class="privboard-row"><td><span class="privboard-position">${it + 1})</span> </td><td style="text-align: right">${member.getScoreFormatted(scoring)}</td>""".trimMargin()

                // info of each star
                val scoreInfo = member.stars.joinToString("") { stars ->
                    val style = when {
                        stars == null to null -> "privboard-star-unlocked"
                        stars.first != null && stars.second == null -> "privboard-star-firstonly"
                        else -> "privboard-star-both"
                    }
                    """<td><span class="$style">*</span></td>"""
                }

                val name = """ <td><span class="privboard-name">${member.name ?: "null"}</span></td></div></tr>"""

                positionInfo + scoreInfo + name
            }.joinToString("")

        generator.loadHtml(HTML_HEADERS + userRows + HTML_FOOTERS)
        generator.saveAsImage(path)

        // image seems to have a column of white pixels on the right edge
        // I cannot see why, so instead just crop the image
        val image = ImageIO.read(File(path)).run{
            getSubimage(0, 0, width - 1, height)
        }
        ImageIO.write(image, "png", File(path))
    }

    enum class SCORING {
        OFFICIAL, DAY_BASED, STAR_COUNT
    }


}