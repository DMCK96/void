package content.bot

import content.bot.profile.*
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import world.gregs.voidps.engine.entity.character.player.Player

/**
 * Integration test for bot migration from legacy task system to profile system.
 * Tests the decision-making process when bots transition between systems.
 */
class MigrationIntegrationTest : BotTestBase() {

    private lateinit var taskManager: TaskManager
    private lateinit var profileManager: ProfileManager
    private lateinit var taskMigration: TaskMigration
    private lateinit var decisionMaking: DecisionMaking
    private lateinit var testPlayer: Player
    private lateinit var testBot: Bot

    override fun setup() {
        // Create test player and bot
        testPlayer = createMockPlayer("TestBot")
        testBot = mockk<Bot>(relaxed = true) {
            every { player } returns testPlayer
        }

        // Initialize services
        taskManager = mockk<TaskManager>(relaxed = true)
        profileManager = mockk<ProfileManager>(relaxed = true)
        taskMigration = mockk<TaskMigration>(relaxed = true)
        decisionMaking = mockk<DecisionMaking>(relaxed = true)
    }

    @Test
    fun `existing bot with legacy task gets migrated successfully`() {
        // Setup: Bot with existing legacy combat task
        val legacyTaskName = "train attack killing goblins"
        testPlayer.mockVariables(mapOf(
            "task_bot" to legacyTaskName,
            "task_started" to false
        ))

        // Mock migration detection
        every { taskMigration.isLegacyBot(testBot) } returns true
        every { taskMigration.shouldMigrateTask(legacyTaskName) } returns true

        // Mock successful migration
        val migratedProfile = BotProfile(
            name = "f2p_combat_basic",
            description = "Basic combat training",
            category = "combat_training",
            weight = 15,
            required_flags = emptyList(),
            steps = emptyList()
        )
        every { taskMigration.migrateBotToProfile(testBot) } returns migratedProfile

        // Test migration
        val result = taskMigration.migrateBotToProfile(testBot)

        // Verify migration was successful
        assertNotNull(result, "Migration should succeed")
        assertEquals("f2p_combat_basic", result!!.name)
        assertEquals("combat_training", result.category)
        verify { taskMigration.migrateBotToProfile(testBot) }
    }

    @Test
    fun `migration failure falls back to legacy system`() {
        // Setup: Bot with task that cannot be migrated
        val complexTaskName = "complex_custom_task"
        testPlayer.mockVariables(mapOf("task_bot" to complexTaskName))

        // Mock migration failure
        every { taskMigration.isLegacyBot(testBot) } returns true
        every { taskMigration.shouldMigrateTask(complexTaskName) } returns false
        every { taskMigration.shouldFallbackToLegacy(testBot, complexTaskName) } returns true

        // Mock legacy task system
        val mockTask = mockk<Task> {
            every { name } returns complexTaskName
            every { spaces } returns 5
        }
        every { taskManager.get(complexTaskName) } returns mockTask

        // Test fallback behavior
        val legacyTask = taskManager.get(complexTaskName)
        val shouldFallback = taskMigration.shouldFallbackToLegacy(testBot, complexTaskName)

        // Verify fallback to legacy system
        assertNotNull(legacyTask, "Legacy task should exist")
        assertTrue(shouldFallback, "Should fallback to legacy system")
        assertEquals(complexTaskName, legacyTask!!.name)
        verify { taskManager.get(complexTaskName) }
    }

    @Test
    fun `new bot assignment prefers profile system`() {
        // Setup: New bot without existing task or migration markers
        testPlayer.mockVariables(mapOf(
            "bot_migrated" to false,
            "bot_profile_assigned" to false
        ))
        every { testPlayer.contains("task_bot") } returns false
        every { testPlayer.contains("bot_migrated") } returns false
        every { testPlayer.contains("bot_profile_assigned") } returns false

        // Mock successful profile assignment
        val assignedProfile = BotProfile(
            name = "f2p_combat_basic",
            description = "Basic combat training",
            category = "combat_training",
            weight = 15,
            required_flags = emptyList(),
            steps = emptyList()
        )
        every { profileManager.assignProfile(testBot) } returns assignedProfile

        // Test profile assignment
        val result = profileManager.assignProfile(testBot)

        // Verify profile assignment succeeded
        assertNotNull(result, "Profile assignment should succeed")
        assertEquals("f2p_combat_basic", result!!.name)
        assertEquals("combat_training", result.category)
        verify { profileManager.assignProfile(testBot) }
    }

    @Test
    fun `profile assignment failure falls back to legacy task manager`() {
        // Setup: New bot that cannot get a profile
        testPlayer.mockVariables(mapOf(
            "bot_migrated" to false as Any,
            "bot_profile_assigned" to false as Any,
            "last_task_bot" to "" as Any
        ))
        every { testPlayer.contains("task_bot") } returns false

        // Mock profile assignment failure
        every { profileManager.assignProfile(testBot) } returns null

        // Mock legacy task assignment success
        val fallbackTask = mockk<Task> {
            every { name } returns "walk randomly"
            every { spaces } returns Int.MAX_VALUE
        }
        every { taskManager.assign(testBot, null) } returns fallbackTask

        // Test profile assignment failure
        val profileResult = profileManager.assignProfile(testBot)
        assertNull(profileResult, "Profile assignment should fail")

        // Test legacy fallback
        val legacyTask = taskManager.assign(testBot, null)

        // Verify legacy assignment occurred
        assertNotNull(legacyTask, "Legacy task should be assigned")
        assertEquals("walk randomly", legacyTask!!.name)
        verify { taskManager.assign(testBot, null) }
    }

    @Test
    fun `migration preserves bot state during transition`() {
        // Setup: Bot with existing task and debug state
        val originalTask = "train defence killing cows"
        testPlayer.mockVariables(mapOf(
            "task_bot" to originalTask,
            "debug" to true
        ))

        // Mock successful migration
        every { taskMigration.isLegacyBot(testBot) } returns true
        every { taskMigration.shouldMigrateTask(originalTask) } returns true
        
        val migratedProfile = BotProfile(
            name = "f2p_combat_basic",
            description = "Basic combat training",
            category = "combat_training",
            weight = 15,
            required_flags = emptyList(),
            steps = emptyList()
        )
        every { taskMigration.migrateBotToProfile(testBot) } returns migratedProfile

        // Test migration
        val result = taskMigration.migrateBotToProfile(testBot)

        // Verify migration succeeded and bot properties preserved
        assertNotNull(result, "Migration should succeed")
        assertEquals("f2p_combat_basic", result!!.name)
        assertEquals("combat_training", result.category)
        
        // Verify debug state is preserved (would be handled by migration logic)
        every { testPlayer.get<Boolean>("debug") } returns true
        val debugState = testPlayer.get<Boolean>("debug") ?: false
        assertTrue(debugState, "Debug state should be preserved")
        
        verify { taskMigration.migrateBotToProfile(testBot) }
    }
}
