package content.bot.profile

import content.bot.Bot
import content.bot.Task
import content.bot.TaskManager
import content.bot.addFlag
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import world.gregs.voidps.engine.entity.character.player.Player
import world.gregs.voidps.type.Tile

/**
 * Full end-to-end test of bot migration from TaskManager to ProfileManager
 * 
 * **Test: Existing bot migration**
 * **Setup:** Bot currently using "train attack killing goblins" task
 * **Action:** Server restart with new profile system  
 * **Expect:** Bot migrated to equivalent combat training profile without interruption
 */
class BotMigrationEndToEndTest {

    private lateinit var profileManager: ProfileManager
    private lateinit var taskMigration: TaskMigration
    private lateinit var taskManager: TaskManager
    private lateinit var testBot: Bot
    private lateinit var player: Player

    @BeforeEach
    fun setup() {
        // Create test player and bot
        player = createMockPlayer()
        testBot = Bot(player)
        
        // Initialize services
        profileManager = ProfileManager()
        taskMigration = TaskMigration()
        taskManager = TaskManager()
        
        // Inject ProfileManager into TaskMigration
        val profileManagerField = TaskMigration::class.java.getDeclaredField("profileManager")
        profileManagerField.isAccessible = true
        profileManagerField.set(taskMigration, profileManager)
    }

    @Test
    fun `existing bot with goblin training task should migrate to combat profile`() {
        // Setup: Bot with existing legacy combat task
        val legacyTaskName = "train attack killing goblins"
        player["task_bot"] = legacyTaskName
        
        // Ensure bot has not been migrated yet
        assertFalse(player.contains("bot_migrated"))
        assertFalse(player.contains("bot_profile_assigned"))
        
        // Verify bot is identified as legacy
        assertTrue(taskMigration.isLegacyBot(testBot))
        
        // Test migration
        val migratedProfile = taskMigration.migrateBotToProfile(testBot)
        
        // Verify successful migration
        assertNotNull(migratedProfile, "Bot should be successfully migrated")
        assertEquals("combat_training", migratedProfile!!.category)
        assertEquals("f2p_combat_basic", migratedProfile.name)
        
        // Verify migration markers are set
        assertTrue(player.contains("bot_migrated"))
        assertTrue(player.contains("bot_profile_assigned"))
        assertEquals("f2p_combat_basic", player["bot_profile_assigned"])
        assertEquals(legacyTaskName, player["bot_migration_from"])
        assertEquals(legacyTaskName, player["last_task_bot"])
    }

    @Test
    fun `bot with woodcutting task should migrate to resource gathering profile`() {
        // Setup: Bot with woodcutting task
        val legacyTaskName = "cut willow trees at draynor"
        player["task_bot"] = legacyTaskName
        
        // Test migration
        val migratedProfile = taskMigration.migrateBotToProfile(testBot)
        
        // Verify migration to correct category
        assertNotNull(migratedProfile)
        assertEquals("resource_gathering", migratedProfile!!.category)
        assertEquals("woodcutting_basic", migratedProfile.name)
    }

    @Test
    fun `bot with custom task should not migrate and allow legacy fallback`() {
        // Setup: Bot with complex custom task that shouldn't be migrated
        val customTaskName = "custom_advanced_dragon_slaying"
        player["task_bot"] = customTaskName
        
        // Verify migration is not attempted for custom tasks
        assertFalse(taskMigration.shouldMigrateTask(customTaskName))
        
        // Verify fallback to legacy is allowed
        assertTrue(taskMigration.shouldFallbackToLegacy(testBot, customTaskName))
        
        // Migration should return null (no mapping available)
        val migratedProfile = taskMigration.migrateBotToProfile(testBot)
        assertNull(migratedProfile, "Custom task should not be migrated")
    }

    @Test
    fun `already migrated bot should not be migrated again`() {
        // Setup: Bot that has already been migrated
        val legacyTaskName = "train attack killing goblins"  
        player["task_bot"] = legacyTaskName
        player["bot_migrated"] = true
        player["bot_profile_assigned"] = "f2p_combat_basic"
        
        // Verify bot is not identified as legacy
        assertFalse(taskMigration.isLegacyBot(testBot))
        
        // Migration should not occur
        val migratedProfile = taskMigration.migrateBotToProfile(testBot)
        assertNull(migratedProfile, "Already migrated bot should not be migrated again")
    }

    @Test
    fun `migration preserves bot progression through flags`() {
        // Setup: Bot with some progression flags
        testBot.addFlag("combat_basics")
        testBot.addFlag("goblin_training_complete")
        
        val legacyTaskName = "train attack killing goblins"
        player["task_bot"] = legacyTaskName
        
        // Test migration
        val migratedProfile = taskMigration.migrateBotToProfile(testBot)
        
        // Verify migration successful
        assertNotNull(migratedProfile)
        
        // Verify bot flags are preserved
        assertTrue(testBot.hasFlag("combat_basics"))
        assertTrue(testBot.hasFlag("goblin_training_complete"))
        
        // Verify migration tracking
        assertTrue(player.contains("bot_migrated"))
        assertEquals(legacyTaskName, player["bot_migration_from"])
    }

    @Test
    fun `migration handles partial matches correctly`() {
        // Test various task name patterns that should match
        val testCases = mapOf(
            "combat training with goblins" to "combat_training",
            "kill chickens for training" to "combat_training", 
            "mine copper ore at lumbridge" to "resource_gathering",
            "fish shrimp at barbarian village" to "resource_gathering",
            "walk around randomly" to "general"
        )
        
        testCases.forEach { (taskName, expectedCategory) ->
            // Reset bot state
            player.clear("bot_migrated")
            player.clear("bot_profile_assigned")
            player["task_bot"] = taskName
            
            val migratedProfile = taskMigration.migrateBotToProfile(testBot)
            
            assertNotNull(migratedProfile, "Task '$taskName' should migrate successfully")
            assertEquals(expectedCategory, migratedProfile!!.category, 
                "Task '$taskName' should migrate to category '$expectedCategory'")
        }
    }

    private fun createMockPlayer(): Player {
        return object : Player(Tile(3200, 3200), "TestBot") {
            private val data = mutableMapOf<String, Any>()
            
            override fun <T> set(key: String, value: T) {
                data[key] = value as Any
            }
            
            override fun <T> get(key: String): T? {
                @Suppress("UNCHECKED_CAST")
                return data[key] as? T
            }
            
            override fun contains(key: String): Boolean = data.containsKey(key)
            
            override fun clear(key: String) {
                data.remove(key)
            }
        }
    }
}
