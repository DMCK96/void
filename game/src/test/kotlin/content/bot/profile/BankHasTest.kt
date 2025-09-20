package content.bot.profile

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse

class BankHasTest {

    @Test
    fun `Test bank_has functionality with Map structure`() {
        // Test context with inventory and bank data as Maps (like ProfileManager provides)
        val context = mapOf(
            "inventory" to mapOf("bronze_sword" to 1, "food" to 5),
            "bank" to mapOf("iron_sword" to 2, "coins" to 1000, "food" to 10),
            "skill_levels" to mapOf("attack" to 15, "strength" to 8),
            "flags" to setOf("combat_ready"),
            "equipment" to listOf("bronze_shield")
        )

        // Test inventory_has with Map structure
        assertTrue(BotStep.evaluateCondition("inventory_has(bronze_sword)", context))  // Should be true
        assertFalse(BotStep.evaluateCondition("inventory_has(iron_sword)", context))   // Should be false
        assertTrue(BotStep.evaluateCondition("inventory_has(food)", context))          // Should be true

        // Test bank_has with Map structure
        assertTrue(BotStep.evaluateCondition("bank_has(iron_sword)", context))         // Should be true
        assertFalse(BotStep.evaluateCondition("bank_has(bronze_sword)", context))      // Should be false
        assertTrue(BotStep.evaluateCondition("bank_has(coins)", context))              // Should be true
        assertTrue(BotStep.evaluateCondition("bank_has(food)", context))               // Should be true
        assertFalse(BotStep.evaluateCondition("bank_has(missing_item)", context))      // Should be false

        // Test other existing conditions still work
        assertTrue(BotStep.evaluateCondition("skill_level(attack, 10)", context))      // Should be true
        assertFalse(BotStep.evaluateCondition("skill_level(attack, 20)", context))     // Should be false
        assertTrue(BotStep.evaluateCondition("has_flag(combat_ready)", context))       // Should be true
        assertTrue(BotStep.evaluateCondition("equipment_has(bronze_shield)", context)) // Should be true
    }

    @Test
    fun `Test bank_has with zero quantities should return false`() {
        val context = mapOf(
            "bank" to mapOf("iron_sword" to 0, "coins" to 5)  // Zero quantity should be treated as not having item
        )

        assertFalse(BotStep.evaluateCondition("bank_has(iron_sword)", context))       // Should be false (zero quantity)
        assertTrue(BotStep.evaluateCondition("bank_has(coins)", context))            // Should be true (positive quantity)
    }
}
