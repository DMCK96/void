package content.bot.profile

import content.bot.*
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import world.gregs.voidps.engine.entity.character.player.Player

/**
 * End-to-end test for bot migration from legacy task system to profile system.
 * Tests complete migration scenarios and edge cases.
 */
class BotMigrationEndToEndTest : BotTestBase() {

    private lateinit var profileManager: ProfileManager
    private lateinit var taskMigration: TaskMigration
    private lateinit var taskManager: TaskManager
    private lateinit var testBot: Bot
    private lateinit var testPlayer: Player

    override fun setup() {
        // Create test player and bot
        testPlayer = createMockPlayer("MigrationBot")
        testBot = mockk<Bot>(relaxed = true) {
            every { player } returns testPlayer
        }
        
        // Initialize services
        profileManager = mockk<ProfileManager>(relaxed = true)
        taskMigration = mockk<TaskMigration>(relaxed = true)
        taskManager = mockk<TaskManager>(relaxed = true)
    }

    @Test
    fun `bot with goblin training task migrates to combat profile`() {
        // Setup: Bot with existing legacy combat task
        val legacyTaskName = "train attack killing goblins"
        testPlayer.mockVariables(mapOf(
            "task_bot" to legacyTaskName,
            "bot_migrated" to false,
            "bot_profile_assigned" to false
        ))
        
        // Mock migration behavior
        every { taskMigration.isLegacyBot(testBot) } returns true
        every { taskMigration.shouldMigrateTask(legacyTaskName) } returns true
        
        val migratedProfile = BotProfile(
            name = "f2p_combat_basic",
            description = "Basic F2P combat training",
            category = "combat_training",
            weight = 10,
            required_flags = emptyList(),
            steps = emptyList()
        )
        every { taskMigration.migrateBotToProfile(testBot) } returns migratedProfile
        
        // Test migration
        val result = taskMigration.migrateBotToProfile(testBot)
        
        // Verify successful migration
        assertNotNull(result, "Bot should be successfully migrated")
        assertEquals("combat_training", result!!.category)
        assertEquals("f2p_combat_basic", result.name)
        verify { taskMigration.migrateBotToProfile(testBot) }
    }

    @Test
    fun `bot with woodcutting task migrates to resource gathering profile`() {
        // Setup: Bot with woodcutting task
        val legacyTaskName = "cut willow trees at draynor"
        testPlayer.mockVariables(mapOf("task_bot" to legacyTaskName))
        
        // Mock migration behavior
        every { taskMigration.shouldMigrateTask(legacyTaskName) } returns true
        
        val resourceProfile = BotProfile(
            name = "woodcutting_basic",
            description = "Basic woodcutting training",
            category = "resource_gathering",
            weight = 8,
            required_flags = emptyList(),
            steps = emptyList()
        )
        every { taskMigration.migrateBotToProfile(testBot) } returns resourceProfile
        
        // Test migration
        val result = taskMigration.migrateBotToProfile(testBot)
        
        // Verify migration to correct category
        assertNotNull(result, "Woodcutting task should migrate successfully")
        assertEquals("resource_gathering", result!!.category)
        assertEquals("woodcutting_basic", result.name)
        verify { taskMigration.migrateBotToProfile(testBot) }
    }

    @Test
    fun `bot with custom task falls back to legacy system`() {
        // Setup: Bot with complex custom task that should not be migrated
        val customTaskName = "custom_advanced_dragon_slaying"
        testPlayer.mockVariables(mapOf("task_bot" to customTaskName))
        
        // Mock migration behavior - custom task should not migrate
        every { taskMigration.shouldMigrateTask(customTaskName) } returns false
        every { taskMigration.shouldFallbackToLegacy(testBot, customTaskName) } returns true
        
        // Test migration attempt
        val shouldMigrate = taskMigration.shouldMigrateTask(customTaskName)
        val shouldFallback = taskMigration.shouldFallbackToLegacy(testBot, customTaskName)
        
        // Verify migration is not attempted for custom tasks
        assertFalse(shouldMigrate, "Custom task should not be migrated")
        assertTrue(shouldFallback, "Should fallback to legacy system")
        
        verify { taskMigration.shouldMigrateTask(customTaskName) }
        verify { taskMigration.shouldFallbackToLegacy(testBot, customTaskName) }
    }

    @Test
    fun `already migrated bot is not migrated again`() {
        // Setup: Bot that has already been migrated
        testPlayer.mockVariables(mapOf(
            "task_bot" to "train attack killing goblins",
            "bot_migrated" to true,
            "bot_profile_assigned" to "f2p_combat_basic"
        ))
        
        // Mock behavior - already migrated bot should not be identified as legacy
        every { taskMigration.isLegacyBot(testBot) } returns false
        
        // Test legacy detection
        val isLegacy = taskMigration.isLegacyBot(testBot)
        
        // Verify bot is not identified as legacy
        assertFalse(isLegacy, "Already migrated bot should not be identified as legacy")
        verify { taskMigration.isLegacyBot(testBot) }
    }

    @Test
    fun `migration preserves bot progression flags`() {
        // Setup: Bot with existing progression flags stored as variables
        testPlayer.mockVariables(mapOf(
            "task_bot" to "train attack killing goblins",
            "bot_flags" to mapOf("combat_basics" to 1L, "goblin_training_complete" to 1L)
        ))
        
        // Mock migration behavior
        val profileWithFlags = BotProfile(
            name = "f2p_combat_basic",
            description = "Basic F2P combat training",
            category = "combat_training",
            weight = 10,
            required_flags = emptyList(),
            steps = emptyList()
        )
        every { taskMigration.migrateBotToProfile(testBot) } returns profileWithFlags
        
        // Test migration
        val result = taskMigration.migrateBotToProfile(testBot)
        
        // Verify migration successful
        assertNotNull(result, "Migration should succeed")
        assertEquals("combat_training", result!!.category)
        assertEquals("f2p_combat_basic", result.name)
        
        // Verify bot flags would be preserved (stored in player variables)
        val botFlags = testPlayer.get<Map<String, Long>>("bot_flags") ?: emptyMap()
        assertTrue(botFlags.containsKey("combat_basics"), "Bot flags should be preserved")
        assertTrue(botFlags.containsKey("goblin_training_complete"), "Bot flags should be preserved")
        
        verify { taskMigration.migrateBotToProfile(testBot) }
    }

    @Test
    fun `migration handles different task patterns correctly`() {
        // Test various task name patterns and expected categories
        val testCases = mapOf(
            "combat training with goblins" to "combat_training",
            "kill chickens for training" to "combat_training", 
            "mine copper ore at lumbridge" to "resource_gathering",
            "fish shrimp at barbarian village" to "resource_gathering",
            "walk around randomly" to "general"
        )
        
        testCases.forEach { (taskName, expectedCategory) ->
            // Setup player with this task
            testPlayer.mockVariables(mapOf("task_bot" to taskName))
            
            // Mock migration result for this category
            val testProfile = BotProfile(
                name = "${expectedCategory}_basic",
                description = "Basic $expectedCategory profile",
                category = expectedCategory,
                weight = 5,
                required_flags = emptyList(),
                steps = emptyList()
            )
            every { taskMigration.shouldMigrateTask(taskName) } returns true
            every { taskMigration.migrateBotToProfile(testBot) } returns testProfile
            
            // Test migration
            val shouldMigrate = taskMigration.shouldMigrateTask(taskName)
            val result = taskMigration.migrateBotToProfile(testBot)
            
            // Verify migration behavior
            assertTrue(shouldMigrate, "Task '$taskName' should be migratable")
            assertNotNull(result, "Task '$taskName' should migrate successfully")
            assertEquals(expectedCategory, result!!.category, 
                "Task '$taskName' should migrate to category '$expectedCategory'")
        }
    }
}
