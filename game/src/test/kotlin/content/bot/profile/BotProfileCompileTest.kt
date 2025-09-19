package content.bot.profile

import org.junit.jupiter.api.Test

/**
 * Simple test to verify BotProfile compiles and basic functionality works
 */
class BotProfileCompileTest {

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
