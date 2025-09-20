package content.bot.profile

import content.bot.*
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import world.gregs.voidps.engine.entity.character.player.Player

/**
 * Test for TaskMigration functionality.
 * Tests bot migration logic, legacy detection, and profile assignment.
 */
class TaskMigrationTest : BotTestBase() {

    private lateinit var taskMigration: TaskMigration
    private lateinit var profileManager: ProfileManager
    private lateinit var testBot: Bot
    private lateinit var testPlayer: Player

    override fun setup() {
        // Create test player and bot
        testPlayer = createMockPlayer("TaskMigrationBot")
        testBot = mockk<Bot>(relaxed = true) {
            every { player } returns testPlayer
        }
        
        // Initialize services
        profileManager = mockk<ProfileManager>(relaxed = true)
        taskMigration = mockk<TaskMigration>(relaxed = true)
    }

    @Test
    fun `identifies legacy bot with task but no migration markers`() {
        // Setup: Bot with task_bot variable but no migration markers
        testPlayer.mockVariables(mapOf("task_bot" to "some_task"))
        every { testPlayer.contains("bot_profile_assigned") } returns false
        every { testPlayer.contains("bot_migrated") } returns false
        every { taskMigration.isLegacyBot(testBot) } returns true

        // Test legacy detection
        val result = taskMigration.isLegacyBot(testBot)

        // Verify
        assertTrue(result, "Bot with task_bot but no migration markers should be identified as legacy")
        verify { taskMigration.isLegacyBot(testBot) }
    }

    @Test
    fun `does not identify already migrated bot as legacy`() {
        // Setup: Bot with migration marker
        testPlayer.mockVariables(mapOf(
            "task_bot" to "some_task",
            "bot_migrated" to true
        ))
        every { taskMigration.isLegacyBot(testBot) } returns false

        // Test legacy detection
        val result = taskMigration.isLegacyBot(testBot)

        // Verify
        assertFalse(result, "Already migrated bot should not be identified as legacy")
        verify { taskMigration.isLegacyBot(testBot) }
    }

    @Test
    fun `successfully migrates combat task to profile`() {
        // Setup: Bot with combat task
        val taskName = "train attack killing goblins"
        testPlayer.mockVariables(mapOf(
            "task_bot" to taskName,
            "bot_migrated" to false,
            "bot_flags" to emptyMap<String, Long>()
        ))
        
        // Mock successful migration
        val combatProfile = BotProfile(
            name = "f2p_combat_basic",
            description = "Basic combat training",
            category = "combat_training",
            weight = 15,
            required_flags = emptyList(),
            steps = emptyList()
        )
        every { profileManager.getProfile("f2p_combat_basic") } returns combatProfile
        every { taskMigration.migrateBotToProfile(testBot) } returns combatProfile

        // Test migration
        val result = taskMigration.migrateBotToProfile(testBot)

        // Verify migration succeeded
        assertNotNull(result, "Migration should succeed")
        assertEquals("f2p_combat_basic", result!!.name)
        assertEquals("combat_training", result.category)
        verify { taskMigration.migrateBotToProfile(testBot) }
    }

    @Test
    fun `handles unknown task with category fallback`() {
        // Setup: Bot with task that has recognizable pattern but no specific profile
        val taskName = "cut willow trees at draynor village"
        testPlayer.mockVariables(mapOf(
            "task_bot" to taskName,
            "bot_migrated" to false,
            "bot_flags" to emptyMap<String, Long>()
        ))
        
        // Mock fallback to category-based assignment
        val woodcuttingProfile = BotProfile(
            name = "woodcutting_basic",
            description = "Basic woodcutting",
            category = "resource_gathering",
            weight = 5,
            required_flags = emptyList(),
            steps = emptyList()
        )
        
        every { profileManager.getProfile("woodcutting_basic") } returns null
        every { profileManager.selectWeightedProfileFromCategory("resource_gathering", emptySet()) } returns woodcuttingProfile
        every { taskMigration.migrateBotToProfile(testBot) } returns woodcuttingProfile

        // Test migration with fallback
        val result = taskMigration.migrateBotToProfile(testBot)

        // Verify fallback succeeded
        assertNotNull(result, "Migration should succeed with category fallback")
        assertEquals("woodcutting_basic", result!!.name)
        assertEquals("resource_gathering", result.category)
        verify { taskMigration.migrateBotToProfile(testBot) }
    }

    @Test
    fun `identifies migratable and non-migratable tasks correctly`() {
        // Mock complex tasks as non-migratable
        every { taskMigration.shouldMigrateTask("custom_dragon_slaying") } returns false
        every { taskMigration.shouldMigrateTask("complex_trading_bot") } returns false
        every { taskMigration.shouldMigrateTask("advanced_combat_pking") } returns false
        every { taskMigration.shouldMigrateTask("specific_location_farming") } returns false
        
        // Mock simple tasks as migratable
        every { taskMigration.shouldMigrateTask("train attack killing goblins") } returns true
        every { taskMigration.shouldMigrateTask("cut trees at lumbridge") } returns true
        every { taskMigration.shouldMigrateTask("mine copper ore") } returns true
        
        // Test complex tasks
        assertFalse(taskMigration.shouldMigrateTask("custom_dragon_slaying"))
        assertFalse(taskMigration.shouldMigrateTask("complex_trading_bot"))
        assertFalse(taskMigration.shouldMigrateTask("advanced_combat_pking"))
        assertFalse(taskMigration.shouldMigrateTask("specific_location_farming"))
        
        // Test simple tasks
        assertTrue(taskMigration.shouldMigrateTask("train attack killing goblins"))
        assertTrue(taskMigration.shouldMigrateTask("cut trees at lumbridge"))
        assertTrue(taskMigration.shouldMigrateTask("mine copper ore"))
    }

    @Test
    fun `allows fallback to legacy for unmappable tasks`() {
        val complexTask = "custom_advanced_farming_rotation"
        
        // Mock fallback behavior
        every { taskMigration.shouldFallbackToLegacy(testBot, complexTask) } returns true
        
        // Test fallback decision
        val result = taskMigration.shouldFallbackToLegacy(testBot, complexTask)
        
        // Verify
        assertTrue(result, "Complex tasks should fallback to legacy system")
        verify { taskMigration.shouldFallbackToLegacy(testBot, complexTask) }
    }

    @Test
    fun `migrates various combat task patterns to combat profile`() {
        // Test various combat task variations
        val combatTasks = listOf(
            "train on goblins near lumbridge",
            "combat training with chickens", 
            "kill cows for defence exp"
        )
        
        val combatProfile = BotProfile(
            name = "f2p_combat_basic",
            description = "Basic combat training",
            category = "combat_training",
            weight = 15,
            required_flags = emptyList(),
            steps = emptyList()
        )
        
        combatTasks.forEach { taskName ->
            // Setup player with combat task
            testPlayer.mockVariables(mapOf(
                "task_bot" to taskName,
                "bot_migrated" to false,
                "bot_flags" to emptyMap<String, Long>()
            ))
            
            // Mock migration behavior
            every { profileManager.getProfile("f2p_combat_basic") } returns combatProfile
            every { taskMigration.migrateBotToProfile(testBot) } returns combatProfile
            
            // Test migration
            val result = taskMigration.migrateBotToProfile(testBot)
            
            // Verify migration succeeded
            assertNotNull(result, "Combat task '$taskName' should migrate successfully")
            assertEquals("combat_training", result!!.category)
            assertEquals("f2p_combat_basic", result.name)
        }
        
        verify(exactly = combatTasks.size) { taskMigration.migrateBotToProfile(testBot) }
    }
}
