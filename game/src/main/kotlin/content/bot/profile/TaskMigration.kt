package content.bot.profile

import content.bot.Bot
import content.bot.TaskManager
import world.gregs.voidps.engine.entity.character.player.Player
import world.gregs.voidps.engine.inject

/**
 * TaskMigration utility for seamlessly transitioning existing bots
 * from the legacy TaskManager system to the new Profile system
 */
class TaskMigration {

    private val profileManager: ProfileManager by inject()

    // Map of common task names to their equivalent profile categories and names
    private val taskToProfileMap = mapOf(
        // Combat tasks
        "train attack killing goblins" to ProfileMapping("combat_training", "f2p_combat_basic"),
        "train defence killing cows" to ProfileMapping("combat_training", "f2p_combat_basic"),
        "train strength killing chickens" to ProfileMapping("combat_training", "f2p_combat_basic"),
        "combat training lumbridge" to ProfileMapping("combat_training", "f2p_combat_basic"),
        
        // Skill-based tasks
        "cut trees at lumbridge" to ProfileMapping("resource_gathering", "woodcutting_basic"),
        "cut willow trees at draynor" to ProfileMapping("resource_gathering", "woodcutting_basic"),
        "mine copper ore at lumbridge" to ProfileMapping("resource_gathering", "mining_basic"),
        "mine tin ore at lumbridge" to ProfileMapping("resource_gathering", "mining_basic"),
        "fish shrimp at draynor" to ProfileMapping("resource_gathering", "fishing_basic"),
        "fish anchovies at draynor" to ProfileMapping("resource_gathering", "fishing_basic"),
        "cook shrimp at lumbridge" to ProfileMapping("resource_gathering", "cooking_basic"),
        "light fires at lumbridge" to ProfileMapping("resource_gathering", "firemaking_basic"),
        
        // General activities
        "walk randomly" to ProfileMapping("general", "idle_bot"),
        "do nothing" to ProfileMapping("general", "idle_bot"),
        
        // Advanced tasks (if available)
        "runecraft air runes" to ProfileMapping("skill_training", "runecrafting_basic"),
        "smith bronze items" to ProfileMapping("resource_gathering", "smithing_basic"),
        "smelt bronze bars" to ProfileMapping("resource_gathering", "smithing_basic"),
    )

    /**
     * Detects if a bot is using the legacy TaskManager system
     * Based on presence of task_bot variable without profile system markers
     */
    fun isLegacyBot(bot: Bot): Boolean {
        val player = bot.player
        return player.contains("task_bot") && !player.contains("bot_profile_assigned") && !player.contains("bot_migrated")
    }

    /**
     * Attempts to migrate a legacy bot to an equivalent profile
     * Returns the assigned profile if successful, null if no mapping exists
     */
    fun migrateBotToProfile(bot: Bot): BotProfile? {
        val player = bot.player
        val currentTaskName: String = player["task_bot"] ?: return null
        
        // Check if already migrated to prevent duplicate migrations
        if (player.contains("bot_migrated")) {
            return null
        }

        // Find equivalent profile mapping
        val mapping = findEquivalentProfile(currentTaskName) ?: return null
        
        // Try to get the specific profile first
        var assignedProfile = profileManager.getProfile(mapping.profileName)
        
        // If specific profile not found, try weighted selection from category
        if (assignedProfile == null) {
            val botFlags = bot.getBotFlags().getFlagNames()
            assignedProfile = profileManager.selectWeightedProfileFromCategory(mapping.category, botFlags)
        }
        
        if (assignedProfile != null) {
            // Mark as migrated and clear legacy task data
            player["bot_migrated"] = true
            player["bot_migration_from"] = currentTaskName
            player["bot_profile_assigned"] = assignedProfile.name
            
            // Store last task for potential fallback
            player["last_task_bot"] = currentTaskName
            
            return assignedProfile
        }
        
        return null
    }

    /**
     * Finds the most appropriate profile mapping for a legacy task name
     * Uses exact matches first, then partial matches
     */
    private fun findEquivalentProfile(taskName: String): ProfileMapping? {
        // Try exact match first
        taskToProfileMap[taskName]?.let { return it }
        
        // Try partial matches for flexibility
        val lowerTaskName = taskName.lowercase()
        
        // Combat-related task patterns
        when {
            lowerTaskName.contains("goblin") || (lowerTaskName.contains("combat") && lowerTaskName.contains("train")) -> 
                return ProfileMapping("combat_training", "f2p_combat_basic")
            lowerTaskName.contains("chicken") && lowerTaskName.contains("train") -> 
                return ProfileMapping("combat_training", "f2p_combat_basic")
            lowerTaskName.contains("cow") && lowerTaskName.contains("train") -> 
                return ProfileMapping("combat_training", "f2p_combat_basic")
            
            // Resource gathering patterns
            lowerTaskName.contains("cut") && lowerTaskName.contains("tree") -> 
                return ProfileMapping("resource_gathering", "woodcutting_basic")
            lowerTaskName.contains("mine") -> 
                return ProfileMapping("resource_gathering", "mining_basic")
            lowerTaskName.contains("fish") -> 
                return ProfileMapping("resource_gathering", "fishing_basic")
            lowerTaskName.contains("cook") -> 
                return ProfileMapping("resource_gathering", "cooking_basic")
            lowerTaskName.contains("fire") || lowerTaskName.contains("light") -> 
                return ProfileMapping("resource_gathering", "firemaking_basic")
            lowerTaskName.contains("smith") -> 
                return ProfileMapping("resource_gathering", "smithing_basic")
            lowerTaskName.contains("smelt") -> 
                return ProfileMapping("resource_gathering", "smithing_basic")
            lowerTaskName.contains("runecraft") -> 
                return ProfileMapping("skill_training", "runecrafting_basic")
            
            // Idle/random activities
            lowerTaskName.contains("walk") || lowerTaskName.contains("random") || lowerTaskName.contains("nothing") -> 
                return ProfileMapping("general", "idle_bot")
        }
        
        return null
    }

    /**
     * Checks if migration should be attempted for a specific task
     * Some tasks may be too complex or specific to migrate automatically
     */
    fun shouldMigrateTask(taskName: String): Boolean {
        // Skip very specific or complex tasks that don't have profile equivalents
        val skipPatterns = listOf(
            "custom_", 
            "complex_", 
            "advanced_combat_",
            "specific_location_"
        )
        
        val lowerTaskName = taskName.lowercase()
        return skipPatterns.none { pattern -> lowerTaskName.contains(pattern) }
    }

    /**
     * Gets migration statistics for monitoring
     */
    fun getMigrationStats(): MigrationStats {
        // This would be enhanced with actual tracking in a production system
        return MigrationStats(
            totalMigrations = 0,
            successfulMigrations = 0,
            failedMigrations = 0,
            commonMigrations = emptyMap()
        )
    }

    /**
     * Provides fallback behavior when migration fails
     * Returns true if bot should continue with legacy TaskManager
     */
    fun shouldFallbackToLegacy(bot: Bot, taskName: String): Boolean {
        // Allow fallback for unmapped tasks to maintain functionality
        return !shouldMigrateTask(taskName) || findEquivalentProfile(taskName) == null
    }
}

/**
 * Represents a mapping from legacy task to profile system
 */
data class ProfileMapping(
    val category: String,
    val profileName: String
)

/**
 * Statistics about migration operations
 */
data class MigrationStats(
    val totalMigrations: Int,
    val successfulMigrations: Int,
    val failedMigrations: Int,
    val commonMigrations: Map<String, Int>
)
