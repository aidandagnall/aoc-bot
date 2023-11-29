import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.interaction.respondEphemeral
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
import io.ktor.http.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.datetime.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.attribute.FileTime
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.setLastModifiedTime

suspend fun main() {

    val kord = Kord(dotenv()["TOKEN"])

    // start thread to update leaderboard in background
    Thread {
        runBlocking {

            var time = if (File("leaderboard.json").exists()) {
                    File("leaderboard.json").toPath()
                        .getLastModifiedTime()
                        .toInstant()
                        .toKotlinInstant()
                } else Clock.System.now().minus(15, DateTimeUnit.MINUTE)
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

    val cooldown = dotenv()["COOLDOWN"]?.let {
        if (it == "") null
        else it.toInt()
    }
    var lastLeaderboardCommand = Clock.System.now().minus(cooldown ?: 0, DateTimeUnit.SECOND)
    // listen for slash commands
    kord.on<GuildChatInputCommandInteractionCreateEvent> {

        val timeSinceLastCommand = lastLeaderboardCommand.until(Clock.System.now(), DateTimeUnit.SECOND)
        if (cooldown != null && timeSinceLastCommand < cooldown) {
            interaction.respondEphemeral {
                content = "Command is on cooldown. Please wait ${cooldown - timeSinceLastCommand} seconds"
            }
            return@on
        }

        val response = interaction.deferPublicResponse()
        val command = interaction.command

        lastLeaderboardCommand = Clock.System.now()

        val scoring = when(command.strings["scoring"]) {
            "official" -> Leaderboard.SCORING.OFFICIAL
            "day-based" -> Leaderboard.SCORING.DAY_BASED
            "star-count" -> Leaderboard.SCORING.STAR_COUNT
            else -> Leaderboard.SCORING.DAY_BASED
        }
        val count = command.integers["users"]
        Leaderboard.fromFile()?.createImage(requestedCount = count?.toInt(), scoring = scoring)

        val images = File(".").walkTopDown().filter { it.name.startsWith("leaderboard")  && it.extension == "png"}
            .sortedBy { it.name }
            .map {
                NamedFile(it.name, ChannelProvider { it.inputStream().toByteReadChannel() })
            }.toMutableList()

        if (images.isEmpty()) {
            response.respond { content = "No images found." }
            return@on
        }
        response.respond {
            files = File(".").walkTopDown().filter { it.name.startsWith("leaderboard")  && it.extension == "png"}
                .sortedBy { it.name }
                .map {
                    NamedFile(it.name, ChannelProvider { it.inputStream().toByteReadChannel() })
                }.toMutableList()
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
    val json = client.get("https://adventofcode.com/$year/leaderboard/private/view/$code.json") {
        cookie(name = "session", value = dotenv()["COOKIE"])
        userAgent(dotenv()["AGENT"])
    }.body<String>()

    File(filename).writeText(json)
    File(filename).toPath().setLastModifiedTime(FileTime.from(Clock.System.now().toJavaInstant()))

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
