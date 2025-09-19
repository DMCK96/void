package content.bot.profile

/**
 * Example usage of the bot flag system
 * This demonstrates how bot flags can be used to track progression and unlock advanced profiles
 */
object BotFlagsExample {

    /**
     * Example of basic flag operations
     */
    fun basicFlagOperations(botFlags: BotFlags) {
        // Add flags to track achievements
        botFlags.addFlag("tutorial_completed")
        botFlags.addFlag("first_combat")
        botFlags.addFlag("basic_gear_acquired")

        // Check if bot has specific achievements
        if (botFlags.hasFlag("tutorial_completed")) {
            println("Bot has completed tutorial")
        }

        // Get all flags
        val flags = botFlags.getAllFlags()
        println("Bot has ${flags.size} flags: ${flags.keys}")

        // Remove a flag
        if (botFlags.removeFlag("first_combat")) {
            println("Removed first_combat flag")
        }
    }

    /**
     * Example of progression tracking with timestamps
     */
    fun progressionTracking(botFlags: BotFlags) {
        // Track when achievements were earned
        botFlags.addFlag("goblin_village_discovered", System.currentTimeMillis())
        botFlags.addFlag("first_quest_completed", System.currentTimeMillis())

        // Check achievement timestamps
        val discoveryTime = botFlags.getFlagTimestamp("goblin_village_discovered")
        if (discoveryTime != null) {
            println("Goblin village discovered at: $discoveryTime")
        }

        // Use timestamps for progression analysis
        val questTime = botFlags.getFlagTimestamp("first_quest_completed")
        if (discoveryTime != null && questTime != null && questTime > discoveryTime) {
            println("Bot completed first quest after discovering goblin village")
        }
    }

    /**
     * Example of advanced profile unlocking based on flags
     */
    fun advancedProfileUnlocking(botFlags: BotFlags): String? {
        val flags = botFlags.getFlagNames()

        return when {
            // Advanced Combat Profile
            flags.containsAll(
                listOf(
                    "combat_training_completed",
                    "weapon_mastery_basic",
                    "armor_equipped_correctly",
                    "food_management_learned",
                ),
            ) -> "advanced_combat_profile"

            // Advanced Skilling Profile
            flags.containsAll(
                listOf(
                    "woodcutting_basics",
                    "mining_basics",
                    "fishing_basics",
                    "resource_management",
                ),
            ) -> "advanced_skilling_profile"

            // Advanced Questing Profile
            flags.containsAll(
                listOf(
                    "dialogue_system_mastered",
                    "inventory_management",
                    "navigation_skills",
                    "three_quests_completed",
                ),
            ) -> "advanced_questing_profile"

            else -> null // No advanced profile available yet
        }
    }

    /**
     * Example of flag-based behavior modification
     */
    fun flagBasedBehavior(botFlags: BotFlags) {
        val behavior = when {
            botFlags.hasFlag("aggressive_combat_preference") -> "aggressive"
            botFlags.hasFlag("defensive_combat_preference") -> "defensive"
            botFlags.hasFlag("mixed_combat_preference") -> "mixed"
            else -> "default"
        }

        println("Bot combat behavior: $behavior")

        // Modify behavior based on experience flags
        if (botFlags.hasFlag("high_level_player")) {
            // Use advanced strategies
            println("Using advanced combat strategies")
        } else if (botFlags.hasFlag("low_level_player")) {
            // Use safe, conservative strategies
            println("Using safe combat strategies")
        }
    }

    /**
     * Example of flag cleanup and management
     */
    fun flagManagement(botFlags: BotFlags) {
        // Clean up temporary flags
        val temporaryFlags = listOf("temp_combat_state", "temp_location_marker", "temp_target")
        temporaryFlags.forEach { flag ->
            if (botFlags.hasFlag(flag)) {
                botFlags.removeFlag(flag)
                println("Cleaned up temporary flag: $flag")
            }
        }

        // Reset all flags if needed (for testing or profile reset)
        if (botFlags.hasFlag("reset_all_progress")) {
            botFlags.clearFlags()
            println("All bot flags cleared for fresh start")
        }
    }

    /**
     * Example of debugging and monitoring flags
     */
    fun debugFlags(botFlags: BotFlags) {
        val flags = botFlags.getAllFlags()

        println("=== Bot Flag Debug Info ===")
        println("Total flags: ${flags.size}")

        flags.entries.sortedBy { it.value }.forEach { (flag, timestamp) ->
            val timeAgo = System.currentTimeMillis() - timestamp
            println("$flag - acquired ${timeAgo}ms ago")
        }

        // Check for specific flag patterns
        val combatFlags = flags.keys.filter { it.startsWith("combat_") }
        val skillFlags = flags.keys.filter { it.startsWith("skill_") }
        val questFlags = flags.keys.filter { it.startsWith("quest_") }

        println("Combat flags: ${combatFlags.size}")
        println("Skill flags: ${skillFlags.size}")
        println("Quest flags: ${questFlags.size}")
    }
}
