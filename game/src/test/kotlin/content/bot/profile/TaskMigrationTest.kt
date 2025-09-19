pacimport content.bot.Bot
import content.bot.TaskManager
import content.bot.getBotFlags
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import world.gregs.voidps.engine.entity.character.player.Player
import world.gregs.voidps.type.Tiletent.bot.profile

import content.bot.Bot
import content.bot.Task
import content.bot.TaskManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import world.gregs.voidps.engine.entity.character.player.Player
import world.gregs.voidps.type.Tile

class TaskMigrationTest {

    private lateinit var taskMigration: TaskMigration
    private lateinit var profileManager: ProfileManager
    private lateinit var mockBot: Bot
    private lateinit var mockPlayer: Player

    @BeforeEach
    fun setup() {
        // Setup mocks
        mockPlayer = mock()
        mockBot = mock()
        profileManager = mock()
        
        // Setup bot and player relationship
        whenever(mockBot.player).thenReturn(mockPlayer)
        whenever(mockPlayer.tile).thenReturn(Tile(3000, 3000))
        
        taskMigration = TaskMigration()
        
        // Mock the profile manager injection
        val profileManagerField = TaskMigration::class.java.getDeclaredField("profileManager")
        profileManagerField.isAccessible = true
        profileManagerField.set(taskMigration, profileManager)
    }

    @Test
    fun `isLegacyBot identifies bot with task_bot but no migration markers`() {
        // Setup: Bot with task_bot variable but no migration markers
        whenever(mockPlayer.contains("task_bot")).thenReturn(true)
        whenever(mockPlayer.contains("bot_profile_assigned")).thenReturn(false)
        whenever(mockPlayer.contains("bot_migrated")).thenReturn(false)

        // Test
        val result = taskMigration.isLegacyBot(mockBot)

        // Verify
        assertTrue(result, "Bot should be identified as legacy")
    }

    @Test
    fun `isLegacyBot returns false for already migrated bot`() {
        // Setup: Bot with migration marker
        whenever(mockPlayer.contains("task_bot")).thenReturn(true)
        whenever(mockPlayer.contains("bot_profile_assigned")).thenReturn(false)
        whenever(mockPlayer.contains("bot_migrated")).thenReturn(true)

        // Test
        val result = taskMigration.isLegacyBot(mockBot)

        // Verify
        assertFalse(result, "Already migrated bot should not be identified as legacy")
    }

    @Test
    fun `migrateBotToProfile successfully migrates combat task`() {
        // Setup: Bot with combat task
        val taskName = "train attack killing goblins"
        whenever(mockPlayer.get<String>("task_bot")).thenReturn(taskName)
        whenever(mockPlayer.contains("bot_migrated")).thenReturn(false)
        
        // Mock profile manager to return a combat profile
        val mockProfile = BotProfile(
            name = "f2p_combat_basic",
            description = "Basic combat training",
            category = "combat_training",
            weight = 15,
            required_flags = emptyList(),
            steps = emptyList()
        )
        whenever(profileManager.getProfile("f2p_combat_basic")).thenReturn(mockProfile)

        // Mock bot flags - return empty map from player to simulate no flags
        whenever(mockPlayer.get<Map<String, Long>>("bot_flags")).thenReturn(emptyMap())

        // Test
        val result = taskMigration.migrateBotToProfile(mockBot)

        // Verify
        assertNotNull(result, "Migration should succeed")
        assertEquals("f2p_combat_basic", result?.name)
        
        // Verify migration markers are set
        verify(mockPlayer).set("bot_migrated", true)
        verify(mockPlayer).set("bot_migration_from", taskName)
        verify(mockPlayer).set("bot_profile_assigned", "f2p_combat_basic")
        verify(mockPlayer).set("last_task_bot", taskName)
    }

    @Test
    fun `migrateBotToProfile handles unknown task with fallback to category`() {
        // Setup: Bot with unknown specific task but recognizable pattern
        val taskName = "cut willow trees at draynor village"
        whenever(mockPlayer.get<String>("task_bot")).thenReturn(taskName)
        whenever(mockPlayer.contains("bot_migrated")).thenReturn(false)
        
        // Mock profile manager to return null for specific profile but success for category
        whenever(profileManager.getProfile("woodcutting_basic")).thenReturn(null)
        
        val mockProfile = BotProfile(
            name = "woodcutting_basic",
            description = "Basic woodcutting",
            category = "resource_gathering",
            weight = 5,
            required_flags = emptyList(),
            steps = emptyList()
        )
        
        // Mock bot flags - return empty map from player to simulate no flags
        whenever(mockPlayer.get<Map<String, Long>>("bot_flags")).thenReturn(emptyMap())
        
        whenever(profileManager.selectWeightedProfileFromCategory("resource_gathering", emptySet()))
            .thenReturn(mockProfile)

        // Test
        val result = taskMigration.migrateBotToProfile(mockBot)

        // Verify
        assertNotNull(result, "Migration should succeed with fallback")
        assertEquals("woodcutting_basic", result?.name)
    }

    @Test
    fun `shouldMigrateTask returns false for complex tasks`() {
        // Test various complex task patterns
        assertFalse(taskMigration.shouldMigrateTask("custom_dragon_slaying"))
        assertFalse(taskMigration.shouldMigrateTask("complex_trading_bot"))
        assertFalse(taskMigration.shouldMigrateTask("advanced_combat_pking"))
        assertFalse(taskMigration.shouldMigrateTask("specific_location_farming"))
        
        // Test that normal tasks return true
        assertTrue(taskMigration.shouldMigrateTask("train attack killing goblins"))
        assertTrue(taskMigration.shouldMigrateTask("cut trees at lumbridge"))
        assertTrue(taskMigration.shouldMigrateTask("mine copper ore"))
    }

    @Test
    fun `shouldFallbackToLegacy returns true for unmappable tasks`() {
        val complexTask = "custom_advanced_farming_rotation"
        
        // Test
        val result = taskMigration.shouldFallbackToLegacy(mockBot, complexTask)
        
        // Verify
        assertTrue(result, "Complex tasks should fallback to legacy system")
    }

    @Test
    fun `partial matching works for combat tasks`() {
        // Test various combat task variations
        val combatTasks = listOf(
            "train on goblins near lumbridge",
            "combat training with chickens",
            "kill cows for defence exp"
        )
        
        combatTasks.forEach { taskName ->
            whenever(mockPlayer.get<String>("task_bot")).thenReturn(taskName)
            whenever(mockPlayer.contains("bot_migrated")).thenReturn(false)
            
            val mockProfile = BotProfile(
                name = "f2p_combat_basic",
                description = "Basic combat",
                category = "combat_training",
                weight = 15,
                required_flags = emptyList(),
                steps = emptyList()
            )
            whenever(profileManager.getProfile("f2p_combat_basic")).thenReturn(mockProfile)
            
            // Mock bot flags - return empty map from player to simulate no flags
            whenever(mockPlayer.get<Map<String, Long>>("bot_flags")).thenReturn(emptyMap())
            
            val result = taskMigration.migrateBotToProfile(mockBot)
            assertNotNull(result, "Combat task '$taskName' should migrate successfully")
            assertEquals("combat_training", result?.category)
        }
    }
}
