package content.bot

import content.bot.profile.ProfileManager
import content.bot.profile.TaskMigration
import content.bot.profile.BotProfile
import content.bot.getBotFlags
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import world.gregs.voidps.engine.entity.character.player.Player
import world.gregs.voidps.engine.entity.character.player.Players
import world.gregs.voidps.type.Tile

class MigrationIntegrationTest {

    private lateinit var decisionMaking: DecisionMaking
    private lateinit var mockTaskManager: TaskManager
    private lateinit var mockProfileManager: ProfileManager
    private lateinit var mockTaskMigration: TaskMigration
    private lateinit var mockPlayers: Players
    private lateinit var mockPlayer: Player
    private lateinit var mockBot: Bot

    @BeforeEach
    fun setup() {
        // Create mocks
        mockTaskManager = mock()
        mockProfileManager = mock()
        mockTaskMigration = mock()
        mockPlayers = mock()
        mockPlayer = mock()
        mockBot = mock()

        // Setup bot and player relationship
        whenever(mockBot.player).thenReturn(mockPlayer)
        whenever(mockPlayer.get<Bot>("bot")).thenReturn(mockBot)
        whenever(mockPlayer.isBot).thenReturn(true)
        whenever(mockPlayer.tile).thenReturn(Tile(3000, 3000))
        whenever(mockPlayer.accountName).thenReturn("TestBot")

        // Create DecisionMaking instance with mocked dependencies
        decisionMaking = DecisionMaking()
        
        // Inject the mocked dependencies using reflection
        val playersField = DecisionMaking::class.java.getDeclaredField("players")
        playersField.isAccessible = true
        playersField.set(decisionMaking, mockPlayers)

        val tasksField = DecisionMaking::class.java.getDeclaredField("tasks")
        tasksField.isAccessible = true
        tasksField.set(decisionMaking, mockTaskManager)

        val profileManagerField = DecisionMaking::class.java.getDeclaredField("profileManager")
        profileManagerField.isAccessible = true
        profileManagerField.set(decisionMaking, mockProfileManager)

        val taskMigrationField = DecisionMaking::class.java.getDeclaredField("taskMigration")
        taskMigrationField.isAccessible = true
        taskMigrationField.set(decisionMaking, mockTaskMigration)
    }

    @Test
    fun `existing bot with legacy task gets migrated on StartBot event`() {
        // Setup: Existing bot with legacy combat task
        val taskName = "train attack killing goblins"
        whenever(mockPlayer.contains("task_bot")).thenReturn(true)
        whenever(mockPlayer.contains("task_started")).thenReturn(false)
        whenever(mockPlayer.get<String>("task_bot")).thenReturn(taskName)

        // Mock migration detection
        whenever(mockTaskMigration.isLegacyBot(mockBot)).thenReturn(true)
        whenever(mockTaskMigration.shouldMigrateTask(taskName)).thenReturn(true)

        // Mock successful migration
        val migratedProfile = BotProfile(
            name = "f2p_combat_basic",
            description = "Basic combat training",
            category = "combat_training",
            weight = 15,
            required_flags = emptyList(),
            steps = emptyList()
        )
        whenever(mockTaskMigration.migrateBotToProfile(mockBot)).thenReturn(migratedProfile)

        // Test: Trigger StartBot event
        // Note: In a real test environment, we would trigger the actual event
        // For this unit test, we'll call the private attemptMigration method directly
        val attemptMigrationMethod = DecisionMaking::class.java.getDeclaredMethod(
            "attemptMigration", Bot::class.java, String::class.java
        )
        attemptMigrationMethod.isAccessible = true
        val result = attemptMigrationMethod.invoke(decisionMaking, mockBot, taskName) as Boolean

        // Verify: Migration was successful
        assertTrue(result, "Migration should succeed")
        verify(mockTaskMigration).migrateBotToProfile(mockBot)
        verify(mockPlayer).clear("task_bot")
        verify(mockPlayer).clear("task_started")
    }

    @Test
    fun `migration failure falls back to legacy system`() {
        // Setup: Bot with task that can't be migrated
        val taskName = "complex_custom_task"
        whenever(mockPlayer.contains("task_bot")).thenReturn(true)
        whenever(mockPlayer.get<String>("task_bot")).thenReturn(taskName)

        // Mock migration failure but allow fallback
        whenever(mockTaskMigration.isLegacyBot(mockBot)).thenReturn(true)
        whenever(mockTaskMigration.shouldMigrateTask(taskName)).thenReturn(false)
        whenever(mockTaskMigration.shouldFallbackToLegacy(mockBot, taskName)).thenReturn(true)

        // Mock legacy task exists
        val mockTask = mock<Task>()
        whenever(mockTaskManager.get(taskName)).thenReturn(mockTask)
        whenever(mockTask.name).thenReturn(taskName)
        whenever(mockTask.spaces).thenReturn(5)

        // Test: Attempt migration
        val attemptMigrationMethod = DecisionMaking::class.java.getDeclaredMethod(
            "attemptMigration", Bot::class.java, String::class.java
        )
        attemptMigrationMethod.isAccessible = true
        val result = attemptMigrationMethod.invoke(decisionMaking, mockBot, taskName) as Boolean

        // Verify: Fallback to legacy system occurred
        assertTrue(result, "Fallback should succeed")
        verify(mockTaskManager).get(taskName)
        verify(mockPlayer).set("task_bot", taskName)
        verify(mockPlayer).set("task_started", true)
    }

    @Test
    fun `new bot assignment prefers profile system`() {
        // Setup: New bot without existing task
        whenever(mockPlayer.contains("task_bot")).thenReturn(false)
        whenever(mockPlayer.contains("bot_migrated")).thenReturn(false)
        whenever(mockPlayer.contains("bot_profile_assigned")).thenReturn(false)

        // Mock successful profile assignment
        val assignedProfile = BotProfile(
            name = "f2p_combat_basic",
            description = "Basic combat training",
            category = "combat_training",
            weight = 15,
            required_flags = emptyList(),
            steps = emptyList()
        )
        whenever(mockProfileManager.assignProfile(mockBot)).thenReturn(assignedProfile)

        // Test: Attempt profile assignment
        val attemptProfileAssignmentMethod = DecisionMaking::class.java.getDeclaredMethod(
            "attemptProfileAssignment", Bot::class.java
        )
        attemptProfileAssignmentMethod.isAccessible = true
        val result = attemptProfileAssignmentMethod.invoke(decisionMaking, mockBot) as Boolean

        // Verify: Profile assignment succeeded
        assertTrue(result, "Profile assignment should succeed")
        verify(mockProfileManager).assignProfile(mockBot)
        verify(mockPlayer).set("bot_profile_assigned", "f2p_combat_basic")
    }

    @Test
    fun `profile assignment failure falls back to legacy task manager`() {
        // Setup: New bot that can't get a profile
        whenever(mockPlayer.contains("task_bot")).thenReturn(false)
        whenever(mockPlayer.contains("bot_migrated")).thenReturn(false)
        whenever(mockPlayer.contains("bot_profile_assigned")).thenReturn(false)
        whenever(mockPlayer.get<String>("last_task_bot")).thenReturn(null)

        // Mock profile assignment failure
        whenever(mockProfileManager.assignProfile(mockBot)).thenReturn(null)

        // Mock legacy task assignment success
        val mockTask = mock<Task>()
        whenever(mockTask.name).thenReturn("walk randomly")
        whenever(mockTask.spaces).thenReturn(Int.MAX_VALUE)
        whenever(mockTaskManager.assign(mockBot, null)).thenReturn(mockTask)

        // Test profile assignment failure
        val attemptProfileAssignmentMethod = DecisionMaking::class.java.getDeclaredMethod(
            "attemptProfileAssignment", Bot::class.java
        )
        attemptProfileAssignmentMethod.isAccessible = true
        val profileResult = attemptProfileAssignmentMethod.invoke(decisionMaking, mockBot) as Boolean

        // Verify profile assignment failed
        assertFalse(profileResult, "Profile assignment should fail")

        // Test legacy fallback (would happen in AiTick event)
        // This simulates the DecisionMaking.assign call
        val assignMethod = DecisionMaking::class.java.getDeclaredMethod(
            "assign", Player::class.java, Task::class.java
        )
        assignMethod.isAccessible = true
        assignMethod.invoke(decisionMaking, mockPlayer, mockTask)

        // Verify legacy assignment occurred
        verify(mockTaskManager).assign(mockBot, null)
        verify(mockPlayer).set("task_bot", "walk randomly")
        verify(mockPlayer).set("task_started", true)
    }

    @Test
    fun `migration preserves bot state during transition`() {
        // Setup: Bot with specific task and state
        val originalTask = "train defence killing cows"
        whenever(mockPlayer.contains("task_bot")).thenReturn(true)
        whenever(mockPlayer.get<String>("task_bot")).thenReturn(originalTask)
        whenever(mockPlayer.get<Boolean>("debug")).thenReturn(true)

        // Mock successful migration
        whenever(mockTaskMigration.isLegacyBot(mockBot)).thenReturn(true)
        whenever(mockTaskMigration.shouldMigrateTask(originalTask)).thenReturn(true)
        
        val migratedProfile = BotProfile(
            name = "f2p_combat_basic",
            description = "Basic combat training",
            category = "combat_training",  
            weight = 15,
            required_flags = emptyList(),
            steps = emptyList()
        )
        whenever(mockTaskMigration.migrateBotToProfile(mockBot)).thenReturn(migratedProfile)

        // Test migration
        val attemptMigrationMethod = DecisionMaking::class.java.getDeclaredMethod(
            "attemptMigration", Bot::class.java, String::class.java
        )
        attemptMigrationMethod.isAccessible = true
        val result = attemptMigrationMethod.invoke(decisionMaking, mockBot, originalTask) as Boolean

        // Verify migration successful and state preserved
        assertTrue(result, "Migration should succeed")
        
        // Verify the last task is preserved for potential rollback
        verify(mockPlayer).set("last_task_bot", originalTask)
        verify(mockPlayer).set("bot_migration_from", originalTask)
        verify(mockPlayer).set("bot_profile_assigned", "f2p_combat_basic")
        verify(mockPlayer).set("bot_migrated", true)
        
        // Verify legacy task state is cleared
        verify(mockPlayer).clear("task_bot")
        verify(mockPlayer).clear("task_started")
    }
}
