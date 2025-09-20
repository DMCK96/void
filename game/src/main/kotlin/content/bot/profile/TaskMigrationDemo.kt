package content.bot.profile

import content.bot.Bot
import content.bot.TaskManager
import content.bot.addFlag
import content.bot.getFlagNames
import world.gregs.voidps.engine.entity.character.player.Player
import world.gregs.voidps.type.Tile

/**
 * Demonstration of the Task Migration system in action
 * Shows how existing bots are seamlessly migrated from TaskManager to ProfileManager
 */
object TaskMigrationDemo {

    fun demonstrateMigration() {
        println("=== Task Migration System Demo ===\n")

        // Initialize services (in real system these would be injected)
        val profileManager = ProfileManager()
        val taskMigration = TaskMigration()
        val taskManager = TaskManager()

        // Inject ProfileManager into TaskMigration
        val profileManagerField = TaskMigration::class.java.getDeclaredField("profileManager")
        profileManagerField.isAccessible = true
        profileManagerField.set(taskMigration, profileManager)

        // Simulate existing bots with legacy tasks
        val legacyBots = createLegacyBots()

        legacyBots.forEach { (bot, taskName) ->
            println("Bot: ${bot.player.accountName}")
            println("Legacy Task: $taskName")
            
            // Check if bot is legacy
            val isLegacy = taskMigration.isLegacyBot(bot)
            println("Is Legacy: $isLegacy")
            
            if (isLegacy && taskMigration.shouldMigrateTask(taskName)) {
                // Attempt migration
                val migratedProfile = taskMigration.migrateBotToProfile(bot)
                
                if (migratedProfile != null) {
                    println("✅ Successfully migrated to: ${migratedProfile.name} (${migratedProfile.category})")
                    println("   Migration markers set: bot_migrated=${bot.player.contains("bot_migrated")}")
                    println("   Profile assigned: ${bot.player.get<String>("bot_profile_assigned")}")
                } else {
                    println("❌ Migration failed - no suitable profile found")
                }
            } else if (!taskMigration.shouldMigrateTask(taskName)) {
                println("⚠️  Task excluded from migration - will use legacy system")
                val shouldFallback = taskMigration.shouldFallbackToLegacy(bot, taskName)
                println("   Legacy fallback allowed: $shouldFallback")
            } else {
                println("ℹ️  Bot already migrated or not eligible")
            }
            
            println("---")
        }

        // Demonstrate profile assignment for new bots
        println("\n=== New Bot Profile Assignment ===")
        val newBot = createNewBot()
        newBot.addFlag("combat_basics") // Give it some progression
        
        println("New Bot: ${newBot.player.accountName}")
        println("Flags: ${newBot.getFlagNames()}")
        
        val assignedProfile = profileManager.assignProfile(newBot, "combat_training")
        if (assignedProfile != null) {
            println("✅ Profile assigned: ${assignedProfile.name}")
            println("   Category: ${assignedProfile.category}")
            println("   Weight: ${assignedProfile.weight}")
        } else {
            println("❌ No suitable profile found")
        }

        // Show migration statistics
        println("\n=== Migration Statistics ===")
        val stats = taskMigration.getMigrationStats()
        println("Total migrations attempted: ${stats.totalMigrations}")
        println("Successful migrations: ${stats.successfulMigrations}")
        println("Failed migrations: ${stats.failedMigrations}")

        // Show profile system stats
        val profileStats = profileManager.getProfileStats()
        println("\n=== Profile System Statistics ===")
        println("Total profiles loaded: ${profileStats.totalProfiles}")
        println("Categories: ${profileStats.categoryCounts}")
        println("Profiles with flags: ${profileStats.profilesWithFlags}")
        println("Profiles with steps: ${profileStats.profilesWithSteps}")
    }

    private fun createLegacyBots(): List<Pair<Bot, String>> {
        return listOf(
            createBotWithTask("GoblinTrainer", "train attack killing goblins"),
            createBotWithTask("TreeCutter", "cut willow trees at draynor"),
            createBotWithTask("MinerBot", "mine copper ore at lumbridge"),
            createBotWithTask("FisherBot", "fish shrimp at draynor"),
            createBotWithTask("CustomBot", "custom_advanced_dragon_slaying"),
            createBotWithTask("IdleBot", "walk randomly"),
            createBotWithTask("CookBot", "cook shrimp at lumbridge")
        )
    }

    private fun createBotWithTask(name: String, taskName: String): Pair<Bot, String> {
        val player = createMockPlayer(name)
        player["task_bot"] = taskName
        
        val bot = Bot(player)
        
        // Add some progression flags to simulate bot history
        when {
            taskName.contains("goblin") -> {
                bot.addFlag("combat_basics")
                bot.addFlag("basic_gear_acquired")
            }
            taskName.contains("tree") -> {
                bot.addFlag("woodcutting_basics")
            }
            taskName.contains("mine") -> {
                bot.addFlag("mining_basics")
            }
            taskName.contains("fish") -> {
                bot.addFlag("fishing_basics")
            }
        }
        
        return bot to taskName
    }

    private fun createNewBot(): Bot {
        val player = createMockPlayer("NewBot")
        return Bot(player)
    }

    private fun createMockPlayer(name: String): Player {
        // Create a simple player instance that works with the bot system
        val player = Player(tile = Tile(3200, 3200), accountName = name)
        return player
    }
}

/**
 * Main function for running the demo
 */
fun main() {
    TaskMigrationDemo.demonstrateMigration()
}
