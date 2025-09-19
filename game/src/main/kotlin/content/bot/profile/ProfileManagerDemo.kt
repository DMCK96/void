package content.bot.profile

/**
 * Demo script to showcase ProfileManager functionality
 * This demonstrates the ProfileManager working with the created TOML profiles
 */
object ProfileManagerDemo {

    fun main() {
        // This would normally be injected, but for demo purposes we create it directly
        val profileManager = ProfileManager()

        println("=== ProfileManager Demo ===")

        // Test profile loading
        println("\n1. Loading all profiles:")
        val allProfiles = profileManager.getAllProfiles()
        println("Loaded ${allProfiles.size} profiles")

        allProfiles.forEach { profile ->
            println("  - ${profile.name} (${profile.category}) - weight: ${profile.weight}")
            if (profile.required_flags.isNotEmpty()) {
                println("    Required flags: ${profile.required_flags}")
            }
        }

        // Test profile lookup
        println("\n2. Profile lookup:")
        val beginnerCombat = profileManager.getProfile("beginner_combat")
        if (beginnerCombat != null) {
            println("Found ${beginnerCombat.name}: ${beginnerCombat.description}")
            println("  Steps: ${beginnerCombat.steps.size}")
            beginnerCombat.steps.forEach { step ->
                println("    - ${step.name}: ${step.description}")
            }
        }

        // Test category filtering
        println("\n3. Category filtering:")
        val combatProfiles = profileManager.getProfilesByCategory("combat")
        println("Combat profiles (${combatProfiles.size}):")
        combatProfiles.forEach { profile ->
            println("  - ${profile.name} (weight: ${profile.weight})")
        }

        val skillingProfiles = profileManager.getProfilesByCategory("skilling")
        println("Skilling profiles (${skillingProfiles.size}):")
        skillingProfiles.forEach { profile ->
            println("  - ${profile.name} (weight: ${profile.weight})")
        }

        // Test flag-based eligibility
        println("\n4. Flag-based eligibility:")
        val noFlags = profileManager.getEligibleProfiles(emptySet())
        println("Profiles eligible with no flags (${noFlags.size}):")
        noFlags.forEach { profile ->
            println("  - ${profile.name}")
        }

        val tutorialFlag = setOf("tutorial_completed")
        val tutorialEligible = profileManager.getEligibleProfiles(tutorialFlag)
        println("Profiles eligible with tutorial flag (${tutorialEligible.size}):")
        tutorialEligible.forEach { profile ->
            println("  - ${profile.name}")
        }

        val advancedFlags = setOf("combat_training_completed", "weapon_mastery_basic", "armor_equipped_correctly")
        val advancedEligible = profileManager.getEligibleProfiles(advancedFlags)
        println("Profiles eligible with advanced flags (${advancedEligible.size}):")
        advancedEligible.forEach { profile ->
            println("  - ${profile.name}")
        }

        // Test statistics
        println("\n5. Profile statistics:")
        val stats = profileManager.getProfileStats()
        println("Total profiles: ${stats.totalProfiles}")
        println("Profiles with flags: ${stats.profilesWithFlags}")
        println("Profiles with steps: ${stats.profilesWithSteps}")
        println("Categories:")
        stats.categoryCounts.entries.sortedBy { it.key }.forEach { (category, count) ->
            println("  - $category: $count")
        }

        // Test available categories
        println("\n6. Available categories:")
        val categories = profileManager.getAvailableCategories()
        println("Categories: ${categories.sorted()}")

        println("\n=== Demo Complete ===")
    }
}
