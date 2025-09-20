package content.bot

import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import world.gregs.voidps.engine.data.ConfigFiles
import world.gregs.voidps.engine.entity.character.player.Player
import world.gregs.voidps.type.Tile

/**
 * Base test class for bot functionality tests.
 * Provides common setup including Koin configuration and mock utilities.
 */
abstract class BotTestBase {

    @BeforeEach
    fun baseSetup() {
        // Clear any existing mocks
        clearAllMocks()
        
        // Initialize Koin with minimal config for testing
        startKoin {
            modules(
                module {
                    single<ConfigFiles> { 
                        mapOf("toml" to listOf("../data/entity/bot/profiles/"))
                    }
                }
            )
        }
        
        // Allow subclasses to perform additional setup
        setup()
    }

    @AfterEach
    fun baseTearDown() {
        // Allow subclasses to perform cleanup
        tearDown()
        
        // Clean up Koin
        stopKoin()
        
        // Clear mocks
        clearAllMocks()
        unmockkAll()
    }

    /**
     * Override in subclasses for additional setup
     */
    protected open fun setup() {}

    /**
     * Override in subclasses for additional cleanup
     */
    protected open fun tearDown() {}

    /**
     * Creates a mock player with proper constructor arguments
     */
    protected fun createMockPlayer(
        accountName: String = "TestBot",
        tile: Tile = Tile(3200, 3200),
        index: Int = 1
    ): Player {
        return mockk<Player>(relaxed = true) {
            every { this@mockk.accountName } returns accountName
            every { this@mockk.tile } returns tile
            every { this@mockk.index } returns index
            every { this@mockk.toString() } returns "Player($accountName, index=$index, tile=$tile)"
        }
    }

    /**
     * Creates a test player instance (not mocked) for integration tests
     */
    protected fun createTestPlayer(
        accountName: String = "TestBot",
        tile: Tile = Tile(3200, 3200),
        index: Int = 1
    ): Player {
        return Player(
            index = index,
            tile = tile,
            accountName = accountName
        )
    }

    /**
     * Sets up a player's variables using MockK
     */
    protected fun Player.mockVariables(variables: Map<String, Any>) {
        variables.forEach { (key, value) ->
            every { get<Any>(key) } returns value
            every { get<Any>(key, any()) } returns value
            every { contains(key) } returns true
        }
    }

    /**
     * Sets up a player's bot flags
     */
    protected fun Player.mockBotFlags(flags: Set<String>) {
        // Mock the extension functions directly on the player mock
        every { this@mockBotFlags.getBotFlags() } returns flags
        flags.forEach { flag ->
            every { this@mockBotFlags.hasFlag(flag) } returns true
        }
    }
}

/**
 * Extension function to check if bot has a flag (for mocked players)
 */
fun Player.hasFlag(flag: String): Boolean = true // Will be mocked in tests

/**
 * Extension function to get bot flags (for mocked players)  
 */
fun Player.getBotFlags(): Set<String> = emptySet() // Will be mocked in tests
