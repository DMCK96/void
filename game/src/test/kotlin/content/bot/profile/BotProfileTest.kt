package content.bot.profile

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import world.gregs.voidps.engine.data.ConfigFiles
import java.io.File

class BotProfileTest {

    @BeforeEach
    fun setup() {
        // Initialize Koin with minimal config for testing
        startKoin {
            modules(
                module {
                    single<ConfigFiles> { 
                        mapOf("toml" to listOf("../data/entity/bot/profiles/combat_training_basic.toml"))
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
    fun `Load combat training profile from file`() {
        val testFile = File("../data/entity/bot/profiles/combat_training_basic.toml")

        // Skip test if file doesn't exist (for CI environments)
        if (!testFile.exists()) {
            return
        }

        val profile = BotProfile.load(testFile)

        assertEquals("combat_training_basic", profile.name)
        assertEquals("Basic combat training profile for low-level players", profile.description)
        assertEquals("combat", profile.category)
        assertEquals(5, profile.weight)
        assertTrue(profile.required_flags.isEmpty())
        assertEquals(4, profile.steps.size)

        // Test first step
        val firstStep = profile.steps[0]
        assertEquals("prepare_equipment", firstStep.name)
        assertEquals("Equip basic combat gear", firstStep.description)
        assertTrue(firstStep.requirements.isEmpty())
        assertEquals(2, firstStep.completion_criteria.size)
        assertTrue(firstStep.completion_criteria.contains("equipment_has(bronze_sword)"))
        assertTrue(firstStep.completion_criteria.contains("equipment_has(bronze_shield)"))
        assertEquals(listOf("combat_gear_equipped"), firstStep.award_flags)
        assertEquals("equipment", firstStep.fallback_category)
    }

    @Test
    fun `Evaluate simple boolean conditions`() {
        val context = mapOf(
            "inventory" to listOf("bronze_sword", "bronze_shield", "food"),
            "skill_levels" to mapOf("attack" to 15, "strength" to 8, "defence" to 5),
            "flags" to setOf("combat_gear_equipped", "basic_training"),
            "equipment" to listOf("bronze_sword", "bronze_shield"),
        )

        // Test inventory_has condition
        assertTrue(BotStep.evaluateCondition("inventory_has(bronze_sword)", context))
        assertFalse(BotStep.evaluateCondition("inventory_has(iron_sword)", context))

        // Test skill_level condition
        assertTrue(BotStep.evaluateCondition("skill_level(attack, 10)", context))
        assertFalse(BotStep.evaluateCondition("skill_level(strength, 10)", context))

        // Test has_flag condition
        assertTrue(BotStep.evaluateCondition("has_flag(combat_gear_equipped)", context))
        assertFalse(BotStep.evaluateCondition("has_flag(advanced_training)", context))

        // Test equipment_has condition
        assertTrue(BotStep.evaluateCondition("equipment_has(bronze_shield)", context))
        assertFalse(BotStep.evaluateCondition("equipment_has(iron_shield)", context))
    }

    @Test
    fun `Evaluate multiple conditions with AND logic`() {
        val context = mapOf(
            "inventory" to listOf("food"),
            "skill_levels" to mapOf("attack" to 15, "strength" to 12),
            "flags" to setOf("combat_gear_equipped"),
            "equipment" to listOf("bronze_sword"),
        )

        val conditions = listOf(
            "skill_level(attack, 10)",
            "skill_level(strength, 10)",
            "has_flag(combat_gear_equipped)",
        )

        assertTrue(BotStep.evaluateConditions(conditions, context))

        val failingConditions = listOf(
            "skill_level(attack, 10)",
            "skill_level(strength, 15)", // This should fail
            "has_flag(combat_gear_equipped)",
        )

        assertFalse(BotStep.evaluateConditions(failingConditions, context))
    }
}
