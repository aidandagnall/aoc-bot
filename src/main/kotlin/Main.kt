import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.channel.GuildMessageChannel
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.core.on
import dev.kord.rest.NamedFile
import dev.kord.rest.builder.interaction.int
import dev.kord.rest.builder.interaction.string
import io.github.cdimascio.dotenv.dotenv
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.until
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File

suspend fun main() {

    val kord = Kord(dotenv()["TOKEN"])

    // start thread to update leaderboard in background
    Thread {
        runBlocking {

            updateLeaderboard(kord)

            var time = Clock.System.now()
            while (true) {
                if (time.until(Clock.System.now(), DateTimeUnit.MINUTE) >= 15) {
                    time = Clock.System.now()
                    updateLeaderboard(kord)
                }
                withContext(Dispatchers.IO) {
                    Thread.sleep(10_000)
                }
            }
        }
    }.start()

    // create /leaderboard command
    kord.createGuildChatInputCommand(
//        guildId = Snowflake(1044932088287207457),
        guildId = Snowflake(dotenv()["SERVER"]),
        name = "leaderboard",
        description = "Show the live leaderboard (up to 15 minute delay)",
    ) {
        string("scoring", "Scoring type to use") {
            choice("official", value = "official")
            choice("day-based", value = "day-based")
            choice("star-count", value = "star-count")
            required = false
        }
        int("users", "Number of users to show") {
            minValue = 1
            maxValue = 200
            required = false
        }
    }

    var lastLeaderboardCommand = Clock.System.now()
    // listen for slash commands
    kord.on<GuildChatInputCommandInteractionCreateEvent> {
        val response = interaction.deferPublicResponse()
        val command = interaction.command

        val timeSinceLastCommand = lastLeaderboardCommand.until(Clock.System.now(), DateTimeUnit.SECOND)
        if (timeSinceLastCommand < 60) {
            response.respond {
                content = "Command is on cooldown. Please wait $timeSinceLastCommand seconds"
            }
            return@on
        }

        lastLeaderboardCommand = Clock.System.now()

        val scoring = when(command.strings["scoring"]) {
            "official" -> Leaderboard.SCORING.OFFICIAL
            "day-based" -> Leaderboard.SCORING.DAY_BASED
            "star-count" -> Leaderboard.SCORING.STAR_COUNT
            else -> Leaderboard.SCORING.DAY_BASED
        }
        val count = command.integers["users"]

        Leaderboard.fromFile()?.createImage(count = count?.toInt(), scoring = scoring)
        response.respond {
            files = mutableListOf(
                NamedFile(
                    "leaderboard.png",
                    ChannelProvider { File("leaderboard.png").inputStream().toByteReadChannel() })
            )
        }
    }

    kord.login()
}

suspend fun updateLeaderboard(kord: Kord) {

    val filename = "leaderboard.json"

    val oldLeaderboard = Leaderboard.fromFile()

    val client = HttpClient(CIO)
    val year = dotenv()["YEAR"]
    val code = dotenv()["CODE"]

    // get new leaderboard
    println("Fetching new leaderboard")
    val json = client.get("https://adventofcode.com/$year/leaderboard/private/view/$code.json")
        { cookie(name = "session", value = dotenv()["COOKIE"]) }.body<String>()

    File(filename).writeText(json)

    if (oldLeaderboard == null) {
        return
    }

    val newLeaderboard = Json.decodeFromString<Leaderboard>(json)

    // if no channel defined then don't print updates
    if (dotenv()["CHANNEL"] == "")
        return

    // get the channel to post updates to
    val channel = runBlocking {
        kord.getChannel(Snowflake(dotenv()["CHANNEL"]))
    } as GuildMessageChannel? ?: return


    newLeaderboard.members.forEach { (id, member) ->

        // if user is not in old leaderboard, don't post updates
        oldLeaderboard.members[id] ?: return@forEach

        // if any stars have been gained in the last 15 minutes, then post a message
        member.stars.forEach {(p1, p2) ->

            p1?.let {
                if (it.getStarTS > Clock.System.now().epochSeconds) {
                    channel.createMessage {
                        content = "${member.name} has completed Day $it Part 1! ⭐️"
                    }
                }
            }

            p2?.let {
                if (it.getStarTS > Clock.System.now().epochSeconds) {
                    channel.createMessage {
                        content = "${member.name} has completed Day $it Part 2! 🌟"
                    }
                }
            }

        }
    }
}
