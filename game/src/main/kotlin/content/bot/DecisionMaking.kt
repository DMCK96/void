package content.bot

import com.github.michaelbull.logging.InlineLogger
import content.bot.interact.navigation.resume
import content.bot.profile.ProfileManager
import content.bot.profile.TaskMigration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import world.gregs.voidps.engine.Contexts
import world.gregs.voidps.engine.entity.AiTick
import world.gregs.voidps.engine.entity.World
import world.gregs.voidps.engine.entity.character.player.Player
import world.gregs.voidps.engine.entity.character.player.Players
import world.gregs.voidps.engine.event.Script
import world.gregs.voidps.engine.event.onEvent
import world.gregs.voidps.engine.inject

@Script
class DecisionMaking {

    val players: Players by inject()
    val tasks: TaskManager by inject()
    val profileManager: ProfileManager by inject()
    val taskMigration: TaskMigration by inject()

    val scope = CoroutineScope(Contexts.Game)
    val logger = InlineLogger("Bot")

    init {
        onEvent<Player, StartBot> { bot ->
            if (!bot.contains("task_bot") || bot.contains("task_started")) {
                return@onEvent
            }
            val name: String = bot["task_bot"]!!
            
            // Check for legacy bot migration opportunity
            val botWrapper = bot["bot"] as? Bot
            if (botWrapper != null && taskMigration.isLegacyBot(botWrapper)) {
                attemptMigration(botWrapper, name)
                return@onEvent
            }
            
            val task = tasks.get(name)
            if (task == null) {
                bot.clear("task_bot")
            } else {
                assign(bot, task)
            }
        }

        onEvent<World, AiTick> {
            players.forEach { player ->
                if (player.isBot) {
                    val bot: Bot = player["bot"]!!
                    if (!player.contains("task_bot")) {
                        val lastTask: String? = player["last_task_bot"]
                        
                        // Prefer profile system for new assignments
                        if (attemptProfileAssignment(bot)) {
                            // Profile assigned successfully, no need for legacy assignment
                        } else {
                            // Fallback to legacy TaskManager
                            assign(player, tasks.assign(bot, lastTask))
                        }
                    }
                    player.bot.resume("tick")
                }
            }
        }
    }

    fun assign(bot: Player, task: Task) {
        if (bot["debug", false]) {
            logger.debug { "Task assigned: ${bot.accountName} - ${task.name}" }
        }
        val last = bot.get<String>("task_bot")
        if (last != null) {
            bot["last_task_bot"] = last
        }
        bot["task_bot"] = task.name
        bot["task_started"] = true
        task.spaces--
        scope.launch {
            try {
                task.block.invoke(bot)
            } catch (t: Throwable) {
                logger.warn(t) { "Task cancelled for $bot" }
            }
            bot.clear("task_bot")
            task.spaces++
        }
    }

    /**
     * Attempts to migrate a legacy bot to the profile system
     * Returns true if migration was successful and bot assignment is complete
     */
    private fun attemptMigration(bot: Bot, currentTaskName: String): Boolean {
        try {
            // Check if this task should be migrated
            if (!taskMigration.shouldMigrateTask(currentTaskName)) {
                if (taskMigration.shouldFallbackToLegacy(bot, currentTaskName)) {
                    logger.debug { "Bot ${bot.player.accountName} staying on legacy task: $currentTaskName" }
                    // Continue with legacy system
                    val task = tasks.get(currentTaskName)
                    if (task != null) {
                        assign(bot.player, task)
                        return true
                    }
                }
                return false
            }

            // Attempt migration
            val migratedProfile = taskMigration.migrateBotToProfile(bot)
            if (migratedProfile != null) {
                logger.info { "Successfully migrated bot ${bot.player.accountName} from task '$currentTaskName' to profile '${migratedProfile.name}'" }
                
                // Clear legacy task data since we're now using profiles
                bot.player.clear("task_bot")
                bot.player.clear("task_started")
                
                // The profile system will handle the actual execution
                // This is just the migration step
                return true
            } else {
                // Migration failed, check if we should fallback
                if (taskMigration.shouldFallbackToLegacy(bot, currentTaskName)) {
                    logger.debug { "Migration failed for bot ${bot.player.accountName}, falling back to legacy task: $currentTaskName" }
                    val task = tasks.get(currentTaskName)
                    if (task != null) {
                        assign(bot.player, task)
                        return true
                    }
                }
                logger.warn { "Migration failed and no fallback available for bot ${bot.player.accountName} task: $currentTaskName" }
                return false
            }
        } catch (e: Exception) {
            logger.error(e) { "Error during migration for bot ${bot.player.accountName}: ${e.message}" }
            
            // On error, fallback to legacy system if possible
            if (taskMigration.shouldFallbackToLegacy(bot, currentTaskName)) {
                val task = tasks.get(currentTaskName)
                if (task != null) {
                    assign(bot.player, task)
                    return true
                }
            }
            return false
        }
    }

    /**
     * Attempts to assign a profile to a bot using the new profile system
     * Returns true if a profile was successfully assigned
     */
    private fun attemptProfileAssignment(bot: Bot): Boolean {
        try {
            // Skip if bot is already migrated or has profile assigned
            if (bot.player.contains("bot_migrated") || bot.player.contains("bot_profile_assigned")) {
                return false
            }

            // Try to assign a profile based on bot characteristics
            val assignedProfile = profileManager.assignProfile(bot)
            if (assignedProfile != null) {
                bot.player["bot_profile_assigned"] = assignedProfile.name
                logger.debug { "Assigned profile '${assignedProfile.name}' to bot ${bot.player.accountName}" }
                return true
            }
            
            return false
        } catch (e: Exception) {
            logger.error(e) { "Error during profile assignment for bot ${bot.player.accountName}: ${e.message}" }
            return false
        }
    }
}
