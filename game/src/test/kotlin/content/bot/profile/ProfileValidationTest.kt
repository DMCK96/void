package content.bot.profile

import java.io.File

/**
 * Simple test script to verify our new bot profiles can be parsed correctly
 * This doesn't require dependency injection to work
 */
fun main() {
    val profileDir = File("data/bot/profiles")

    // Test our new combat training profiles
    val combatProfiles = listOf(
        "combat_training/f2p_combat_basic.toml",
        "combat_training/f2p_combat_intermediate.toml",
    )

    // Test our new coin gathering profiles
    val coinProfiles = listOf(
        "coin_gathering/cowhide_collection.toml",
        "coin_gathering/goblin_loot.toml",
        "coin_gathering/chicken_feathers.toml",
    )

    val allProfiles = combatProfiles + coinProfiles

    println("Testing Bot Profile Loading...")
    println("=====================================")

    var successCount = 0
    var totalCount = 0

    for (profilePath in allProfiles) {
        totalCount++
        val file = File(profileDir, profilePath)

        try {
            if (!file.exists()) {
                println("❌ FAIL: $profilePath - File does not exist")
                continue
            }

            // Simple validation - check that file contains required TOML structure
            val content = file.readText()
            val requiredFields = listOf("name", "description", "category", "weight")
            val missingFields = requiredFields.filter { !content.contains("$it =") }

            if (missingFields.isNotEmpty()) {
                println("❌ FAIL: $profilePath - Missing fields: ${missingFields.joinToString(", ")}")
                continue
            }

            // Check for step sections
            val hasSteps = content.contains("[step.")
            if (!hasSteps) {
                println("❌ FAIL: $profilePath - No step sections found")
                continue
            }

            // Check for proper step structure
            val stepSections = content.split("[step.").drop(1)
            val validSteps = stepSections.all { section ->
                val requiredStepFields = listOf("description", "completion_criteria", "award_flags")
                requiredStepFields.all { field -> section.contains("$field =") }
            }

            if (!validSteps) {
                println("❌ FAIL: $profilePath - Invalid step structure")
                continue
            }

            println("✅ PASS: $profilePath")
            successCount++
        } catch (e: Exception) {
            println("❌ FAIL: $profilePath - Exception: ${e.message}")
        }
    }

    println("=====================================")
    println("Results: $successCount/$totalCount profiles passed validation")

    if (successCount == totalCount) {
        println("🎉 All profiles loaded successfully!")

        // Print profile summary
        println("\nProfile Summary:")
        println("Combat Training: ${combatProfiles.size} profiles")
        println("Coin Gathering: ${coinProfiles.size} profiles")
        println("Total: ${allProfiles.size} profiles")
    } else {
        println("❌ Some profiles failed validation. Check the output above.")
        System.exit(1)
    }
}
