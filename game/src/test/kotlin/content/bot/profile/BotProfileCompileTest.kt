package content.bot.profile

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import world.gregs.voidps.engine.data.ConfigFiles

/**
 * Simple test to verify BotProfile compiles and basic functionality works
 */
class BotProfileCompileTest {

    @BeforeEach
    fun setup() {
        // Initialize Koin with minimal config for testing
        startKoin {
            modules(
                module {
                    single<ConfigFiles> { 
                        mapOf("toml" to emptyList())
                    }
                }
            )
        }
    }

    @AfterEach
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `BotProfile can be instantiated`() {
        val profile = BotProfile(
            name = "test_profile",
            description = "A test profile",
            category = "test",
            weight = 1,
            required_flags = emptyList(),
            steps = emptyList(),
        )

        assert(profile.name == "test_profile")
        assert(profile.category == "test")
        assert(profile.weight == 1)
    }

    @Test
    fun `BotStep conditions can be evaluated`() {
        val context = mapOf(
            "inventory" to listOf("test_item"),
            "skill_levels" to mapOf("test_skill" to 10),
            "flags" to setOf("test_flag"),
            "equipment" to listOf("test_equipment"),
        )

        assert(BotStep.evaluateCondition("inventory_has(test_item)", context))
        assert(BotStep.evaluateCondition("skill_level(test_skill, 5)", context))
        assert(BotStep.evaluateCondition("has_flag(test_flag)", context))
        assert(BotStep.evaluateCondition("equipment_has(test_equipment)", context))

        assert(!BotStep.evaluateCondition("inventory_has(missing_item)", context))
        assert(!BotStep.evaluateCondition("skill_level(test_skill, 15)", context))
        assert(!BotStep.evaluateCondition("has_flag(missing_flag)", context))
        assert(!BotStep.evaluateCondition("equipment_has(missing_equipment)", context))
    }
}
