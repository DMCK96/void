// Simple test to verify bank_has functionality
import content.bot.profile.BotStep

fun main() {
    // Test context with inventory and bank data as Maps (like ProfileManager provides)
    val context = mapOf(
        "inventory" to mapOf("bronze_sword" to 1, "food" to 5),
        "bank" to mapOf("iron_sword" to 2, "coins" to 1000, "food" to 10),
        "skill_levels" to mapOf("attack" to 15, "strength" to 8),
        "flags" to setOf("combat_ready"),
        "equipment" to listOf("bronze_shield")
    )

    // Test inventory_has with Map structure
    println("Testing inventory_has:")
    println("inventory_has(bronze_sword): ${BotStep.evaluateCondition("inventory_has(bronze_sword)", context)}")  // Should be true
    println("inventory_has(iron_sword): ${BotStep.evaluateCondition("inventory_has(iron_sword)", context)}")      // Should be false
    println("inventory_has(food): ${BotStep.evaluateCondition("inventory_has(food)", context)}")                  // Should be true

    // Test bank_has with Map structure
    println("\nTesting bank_has:")
    println("bank_has(iron_sword): ${BotStep.evaluateCondition("bank_has(iron_sword)", context)}")                // Should be true
    println("bank_has(bronze_sword): ${BotStep.evaluateCondition("bank_has(bronze_sword)", context)}")            // Should be false
    println("bank_has(coins): ${BotStep.evaluateCondition("bank_has(coins)", context)}")                          // Should be true
    println("bank_has(food): ${BotStep.evaluateCondition("bank_has(food)", context)}")                            // Should be true
    println("bank_has(missing_item): ${BotStep.evaluateCondition("bank_has(missing_item)", context)}")            // Should be false

    // Test other existing conditions still work
    println("\nTesting other conditions:")
    println("skill_level(attack, 10): ${BotStep.evaluateCondition("skill_level(attack, 10)", context)}")         // Should be true
    println("skill_level(attack, 20): ${BotStep.evaluateCondition("skill_level(attack, 20)", context)}")         // Should be false
    println("has_flag(combat_ready): ${BotStep.evaluateCondition("has_flag(combat_ready)", context)}")            // Should be true
    println("equipment_has(bronze_shield): ${BotStep.evaluateCondition("equipment_has(bronze_shield)", context)}")// Should be true

    println("\nAll tests completed!")
}
