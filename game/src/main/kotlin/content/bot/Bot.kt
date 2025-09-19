package content.bot

import content.bot.profile.BotFlags
import world.gregs.voidps.engine.entity.character.Character
import world.gregs.voidps.engine.entity.character.player.Player
import world.gregs.voidps.network.client.Instruction

data class Bot(val player: Player) : Character by player {
    var step: Instruction? = null
}

/**
 * Bot flag management extension functions
 */

/**
 * Gets the bot flags for this bot, creating an empty set if none exist
 */
fun Bot.getBotFlags(): BotFlags {
    val flagsMap = player["bot_flags"] as? Map<String, Long> ?: emptyMap()
    return BotFlags.fromMap(flagsMap)
}

/**
 * Checks if the bot has a specific flag
 */
fun Bot.hasFlag(flag: String): Boolean = getBotFlags().hasFlag(flag)

/**
 * Adds a flag to the bot with current timestamp
 */
fun Bot.addFlag(flag: String) {
    val flags = getBotFlags()
    flags.addFlag(flag)
    player["bot_flags"] = flags.getAllFlags()
}

/**
 * Adds a flag to the bot with specific timestamp
 */
fun Bot.addFlag(flag: String, timestamp: Long) {
    val flags = getBotFlags()
    flags.addFlag(flag, timestamp)
    player["bot_flags"] = flags.getAllFlags()
}

/**
 * Removes a flag from the bot
 * @return true if the flag was present and removed, false otherwise
 */
fun Bot.removeFlag(flag: String): Boolean {
    val flags = getBotFlags()
    val removed = flags.removeFlag(flag)
    if (removed) {
        if (flags.isEmpty()) {
            player.clear("bot_flags")
        } else {
            player["bot_flags"] = flags.getAllFlags()
        }
    }
    return removed
}

/**
 * Gets the timestamp when a flag was added
 * @return timestamp in milliseconds, or null if flag not present
 */
fun Bot.getFlagTimestamp(flag: String): Long? = getBotFlags().getFlagTimestamp(flag)

/**
 * Gets all flags as a read-only map
 */
fun Bot.getFlags(): Map<String, Long> = getBotFlags().getAllFlags()

/**
 * Gets all flag names
 */
fun Bot.getFlagNames(): Set<String> = getBotFlags().getFlagNames()

/**
 * Clears all flags from the bot
 */
fun Bot.clearFlags() {
    player.clear("bot_flags")
}
