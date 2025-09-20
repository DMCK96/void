package content.bot.profile

import content.bot.addFlag
import content.bot.getBotFlags
import content.entity.death.weightedSample
import content.entity.player.bank.bank
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import world.gregs.voidps.engine.data.ConfigFiles
import world.gregs.voidps.engine.data.list
import world.gregs.voidps.engine.entity.character.player.combatLevel
import world.gregs.voidps.engine.inject
import world.gregs.voidps.engine.inv.equipment
import world.gregs.voidps.engine.inv.inventory
import world.gregs.voidps.engine.timedLoad
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * ProfileManager service that loads and caches bot profiles from TOML files
 *
 * This service provides global profile sharing across all bot instances
 * and implements profile caching for performance optimization.
 */
class ProfileManager {

    private val configFiles: ConfigFiles by inject()

    // Profile cache for performance optimization
    private val profileCache = ConcurrentHashMap<String, BotProfile>()
    private val profilesByCategory = ConcurrentHashMap<String, List<BotProfile>>()
    private val categoriesCache = ConcurrentHashMap<String, ProfileCategory>()
    private val allProfilesCache = ObjectArrayList<BotProfile>()

    // Loading state tracking
    @Volatile
    private var profilesLoaded = false

    /**
     * Load all profiles from TOML files using ConfigFiles system
     * Implements lazy loading with caching for performance
     */
    private fun loadProfiles() {
        if (profilesLoaded) return

        synchronized(this) {
            if (profilesLoaded) return

            val loadedCount = timedLoad("bot profiles") {
                val tomlFiles: List<String> = configFiles.list("toml")
                val profileFiles = tomlFiles.filter { filePath: String ->
                    filePath.contains("bot/profiles/")
                }

                var loadedCount = 0
                var errorCount = 0

                profileFiles.forEach { filePath: String ->
                    try {
                        val profile = BotProfile.load(File(filePath))

                        // Validate profile before caching
                        if (validateProfile(profile)) {
                            profileCache[profile.name] = profile
                            allProfilesCache.add(profile)
                            loadedCount++
                        } else {
                            println("Profile validation failed for: ${profile.name} from $filePath")
                            errorCount++
                        }
                    } catch (e: Exception) {
                        println("Failed to load bot profile from $filePath: ${e.message}")
                        errorCount++
                    }
                }

                // Build category cache
                buildCategoryCache()

                if (errorCount > 0) {
                    println("Loaded $loadedCount bot profiles with $errorCount errors")
                }

                loadedCount // Return count for timedLoad
            }

            profilesLoaded = true
        }
    }

    /**
     * Build category-based cache for efficient lookups
     */
    private fun buildCategoryCache() {
        val categoryMap = Object2ObjectOpenHashMap<String, MutableList<BotProfile>>()

        allProfilesCache.forEach { profile ->
            val category = profile.category.ifEmpty { "general" }
            categoryMap.getOrPut(category) { ObjectArrayList() }.add(profile)
        }

        // Sort profiles within each category by weight (descending) and cache both formats
        categoryMap.forEach { (categoryName, profiles) ->
            profiles.sortByDescending { it.weight }
            profilesByCategory[categoryName] = profiles
            categoriesCache[categoryName] = ProfileCategory(
                name = categoryName,
                profiles = profiles,
            )
        }
    }

    /**
     * Validate a profile during loading
     */
    private fun validateProfile(profile: BotProfile): Boolean {
        // Check for required fields
        if (profile.name.isBlank()) {
            println("Profile validation failed: name is blank")
            return false
        }

        // Check for duplicate names
        if (profileCache.containsKey(profile.name)) {
            println("Profile validation failed: duplicate name '${profile.name}'")
            return false
        }

        // Validate steps if present
        profile.steps.forEach { step ->
            if (step.name.isBlank()) {
                println("Profile validation failed: step with blank name in profile '${profile.name}'")
                return false
            }
        }

        return true
    }

    /**
     * Get a specific profile by name
     * Returns null if profile not found
     */
    fun getProfile(name: String): BotProfile? {
        loadProfiles()
        return profileCache[name]
    }

    /**
     * Get all loaded profiles
     * Returns a copy to prevent external modification
     */
    fun getAllProfiles(): List<BotProfile> {
        loadProfiles()
        return ObjectArrayList(allProfilesCache)
    }

    /**
     * Get profiles by category
     * Returns empty list if category not found
     */
    fun getProfilesByCategory(category: String): List<BotProfile> {
        loadProfiles()
        val normalizedCategory = category.ifEmpty { "general" }
        return profilesByCategory[normalizedCategory] ?: emptyList()
    }

    /**
     * Get profiles that match multiple names
     * Useful for batch operations
     */
    fun getProfilesByName(names: Collection<String>): List<BotProfile> {
        loadProfiles()
        return names.mapNotNull { profileCache[it] }
    }

    /**
     * Get profiles that have all required flags satisfied
     */
    fun getEligibleProfiles(playerFlags: Set<String>, category: String = ""): List<BotProfile> {
        loadProfiles()

        val candidateProfiles = if (category.isNotEmpty()) {
            getProfilesByCategory(category)
        } else {
            allProfilesCache
        }

        return candidateProfiles.filter { profile ->
            profile.required_flags.all { flag -> playerFlags.contains(flag) }
        }.sortedByDescending { it.weight }
    }

    /**
     * Get all available categories
     */
    fun getAvailableCategories(): Set<String> {
        loadProfiles()
        return profilesByCategory.keys.toSet()
    }

    /**
     * Get profile count statistics
     */
    fun getProfileStats(): ProfileStats {
        loadProfiles()
        return ProfileStats(
            totalProfiles = allProfilesCache.size,
            categoryCounts = profilesByCategory.mapValues { it.value.size },
            profilesWithFlags = allProfilesCache.count { it.required_flags.isNotEmpty() },
            profilesWithSteps = allProfilesCache.count { it.steps.isNotEmpty() },
        )
    }

    /**
     * Force reload all profiles (useful for development/testing)
     */
    fun reloadProfiles() {
        synchronized(this) {
            profileCache.clear()
            profilesByCategory.clear()
            categoriesCache.clear()
            allProfilesCache.clear()
            profilesLoaded = false
            loadProfiles()
        }
    }

    /**
     * Get a ProfileCategory by name
     * Returns null if category not found
     */
    fun getCategory(categoryName: String): ProfileCategory? {
        loadProfiles()
        val normalizedCategory = categoryName.ifEmpty { "general" }
        return categoriesCache[normalizedCategory]
    }

    /**
     * Get all available ProfileCategory instances
     */
    fun getAllCategories(): List<ProfileCategory> {
        loadProfiles()
        return categoriesCache.values.toList()
    }

    /**
     * Select a random profile from a category using weighted distribution
     * Higher weight profiles are more likely to be selected
     *
     * @param categoryName The category to select from
     * @param playerFlags Optional player flags for eligibility filtering
     * @return A randomly selected profile, or null if category is empty or no eligible profiles
     */
    fun selectWeightedProfileFromCategory(categoryName: String, playerFlags: Set<String> = emptySet()): BotProfile? {
        val category = getCategory(categoryName) ?: return null

        return if (playerFlags.isEmpty()) {
            category.selectWeightedProfile()
        } else {
            category.selectWeightedEligibleProfile(playerFlags)
        }
    }

    /**
     * Select a profile from a category with weighted distribution and fallback
     * Tries the specified category first, then falls back to general category
     *
     * @param categoryName Primary category to try
     * @param playerFlags Player flags for eligibility filtering
     * @return A randomly selected profile, or null if no suitable profiles found
     */
    fun selectWeightedProfileWithFallback(categoryName: String, playerFlags: Set<String> = emptySet()): BotProfile? {
        // Try primary category first
        var profile = selectWeightedProfileFromCategory(categoryName, playerFlags)

        // Fallback to general category if no match found
        if (profile == null && categoryName != "general") {
            profile = selectWeightedProfileFromCategory("general", playerFlags)
        }

        // Final fallback - try any category with weighted selection
        if (profile == null) {
            val eligibleProfiles = getEligibleProfiles(playerFlags, "")
            if (eligibleProfiles.isNotEmpty()) {
                val weightedProfiles = eligibleProfiles.map { it to it.weight }
                profile = weightedSample(weightedProfiles)
            }
        }

        return profile
    }

    /**
     * Check if profiles have been loaded
     */
    fun isLoaded(): Boolean = profilesLoaded

    /**
     * Assigns the most suitable profile to a bot based on characteristics and flag dependencies
     * Completes within 100ms performance constraint through efficient filtering
     *
     * @param bot The bot to assign a profile to
     * @param preferredCategory Optional category to prioritize (empty = all categories)
     * @return The assigned profile, or null if no suitable profiles are available
     */
    fun assignProfile(bot: content.bot.Bot, preferredCategory: String = ""): BotProfile? {
        val startTime = System.currentTimeMillis()
        loadProfiles()

        try {
            // Get bot characteristics for evaluation
            val botCharacteristics = evaluateBotCharacteristics(bot)
            val botFlags = bot.getBotFlags().getFlagNames()

            // Get candidate profiles based on category preference
            val candidateProfiles = if (preferredCategory.isNotEmpty()) {
                getProfilesByCategory(preferredCategory)
            } else {
                allProfilesCache
            }

            // Filter profiles based on flag dependencies and characteristics
            val eligibleProfiles = candidateProfiles.filter { profile ->
                isProfileEligible(profile, botFlags, botCharacteristics)
            }

            // Use weighted selection for diverse bot distribution, or fallback to highest weight
            val assignedProfile = if (eligibleProfiles.isNotEmpty()) {
                val weightedProfiles = eligibleProfiles.map { it to it.weight }
                weightedSample(weightedProfiles) ?: eligibleProfiles.maxByOrNull { it.weight }
            } else {
                null
            }

            // Performance monitoring - should complete within 100ms
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed > 100) {
                println("Warning: Profile assignment took ${elapsed}ms (target: <100ms)")
            }

            return assignedProfile
        } catch (e: Exception) {
            println("Error during profile assignment: ${e.message}")
            return null
        }
    }

    /**
     * Evaluates bot characteristics for profile matching
     * Extracts levels, equipment, location, and other relevant state
     */
    private fun evaluateBotCharacteristics(bot: content.bot.Bot): BotCharacteristics {
        val player = bot.player

        return BotCharacteristics(
            combatLevel = player.combatLevel,
            totalLevel = calculateTotalLevel(player),
            location = player.tile,
            hasWeapon = !player.equipment[0].isEmpty(), // Weapon slot
            hasArmor = !player.equipment[4].isEmpty() || !player.equipment[7].isEmpty(), // Chest or legs
            inventoryCount = player.inventory.count,
            highestSkillLevel = getHighestSkillLevel(player),
            questCount = getQuestCount(player),
        )
    }

    /**
     * Checks if a profile is eligible based on flag dependencies and characteristics
     */
    private fun isProfileEligible(
        profile: BotProfile,
        botFlags: Set<String>,
        characteristics: BotCharacteristics,
    ): Boolean {
        // Check flag dependencies - all required flags must be present
        if (!profile.required_flags.all { flag -> botFlags.contains(flag) }) {
            return false
        }

        // Basic characteristic validation
        if (!validateBasicRequirements(profile, characteristics)) {
            return false
        }

        return true
    }

    /**
     * Validates basic profile requirements against bot characteristics
     */
    private fun validateBasicRequirements(profile: BotProfile, characteristics: BotCharacteristics): Boolean {
        // Example basic validations - can be extended based on profile definitions

        // Combat profiles might require minimum combat level
        if (profile.category == "combat" && characteristics.combatLevel < 10) {
            return false
        }

        // Skilling profiles might require certain equipment
        if (profile.category == "skilling" && characteristics.inventoryCount == 0) {
            return false
        }

        // Advanced profiles might require quest progress
        if (profile.weight > 5 && characteristics.questCount < 1) {
            return false
        }

        return true
    }

    /**
     * Calculates total skill level for bot evaluation
     */
    private fun calculateTotalLevel(player: world.gregs.voidps.engine.entity.character.player.Player): Int = world.gregs.voidps.engine.entity.character.player.skill.Skill.all.sumOf { skill ->
        if (skill == world.gregs.voidps.engine.entity.character.player.skill.Skill.Constitution) {
            player.levels.getMax(skill) / 10
        } else {
            player.levels.getMax(skill)
        }
    }

    /**
     * Gets the highest skill level for bot evaluation
     */
    private fun getHighestSkillLevel(player: world.gregs.voidps.engine.entity.character.player.Player): Int = world.gregs.voidps.engine.entity.character.player.skill.Skill.all.maxOf { skill ->
        if (skill == world.gregs.voidps.engine.entity.character.player.skill.Skill.Constitution) {
            player.levels.getMax(skill) / 10
        } else {
            player.levels.getMax(skill)
        }
    }

    /**
     * Gets quest completion count for bot evaluation
     */
    private fun getQuestCount(player: world.gregs.voidps.engine.entity.character.player.Player): Int {
        // Simplified quest counting - would need actual quest system integration
        return player.variables.data.keys.count { it.contains("quest") && it.contains("complete") }
    }

    /**
     * Assigns a profile to a bot with fallback handling using weighted selection
     * Tries preferred category first with weighted distribution, then falls back to general category
     *
     * @param bot The bot to assign a profile to
     * @param preferredCategory Primary category to try
     * @return The assigned profile, or null if no profiles are suitable
     */
    fun assignProfileWithFallback(bot: content.bot.Bot, preferredCategory: String): BotProfile? {
        val botFlags = bot.getBotFlags().getFlagNames()

        // Use weighted selection for better distribution
        return selectWeightedProfileWithFallback(preferredCategory, botFlags)
    }

    /**
     * Handles step failure and attempts fallback category assignment
     * When a bot step fails and has a fallback_category defined, this method
     * assigns a new profile from that category using weighted selection
     *
     * @param bot The bot whose step failed
     * @param failedStep The step that failed
     * @return A new profile from the fallback category, or null if none available
     */
    fun handleStepFailbackAssignment(bot: content.bot.Bot, failedStep: BotStep): BotProfile? {
        if (failedStep.fallback_category.isEmpty()) {
            return null
        }

        val botFlags = bot.getBotFlags().getFlagNames()
        return selectWeightedProfileFromCategory(failedStep.fallback_category, botFlags)
    }

    /**
     * Executes step requirements and completion checks with fallback support
     * Evaluates step requirements, and if they fail and fallback_category is defined,
     * attempts to assign a profile from the fallback category
     *
     * @param bot The bot executing the step
     * @param step The step to execute
     * @param context Evaluation context for step conditions
     * @return ExecutionResult indicating success, failure, or fallback assignment
     */
    fun executeStepWithFallback(bot: content.bot.Bot, step: BotStep, context: Map<String, Any>): StepExecutionResult {
        // Check if step requirements are met
        val requirementsMet = BotStep.evaluateConditions(step.requirements, context)

        if (!requirementsMet) {
            // Requirements not met - attempt fallback if available
            if (step.fallback_category.isNotEmpty()) {
                val fallbackProfile = handleStepFailbackAssignment(bot, step)
                return if (fallbackProfile != null) {
                    StepExecutionResult.FallbackAssigned(fallbackProfile)
                } else {
                    StepExecutionResult.Failed("Requirements not met and no fallback profile available")
                }
            } else {
                return StepExecutionResult.Failed("Requirements not met: ${step.requirements}")
            }
        }

        // Requirements met - check completion criteria
        val completed = BotStep.evaluateConditions(step.completion_criteria, context)

        return if (completed) {
            // Award flags for completion
            step.award_flags.forEach { flag ->
                bot.addFlag(flag)
            }
            StepExecutionResult.Completed(step.award_flags)
        } else {
            StepExecutionResult.InProgress
        }
    }

    /**
     * Validates profile eligibility without assignment
     * Useful for checking if a specific profile can be assigned to a bot
     */
    fun isProfileAssignable(bot: content.bot.Bot, profileName: String): Boolean {
        val profile = getProfile(profileName) ?: return false
        val botCharacteristics = evaluateBotCharacteristics(bot)
        val botFlags = bot.getBotFlags().getFlagNames()

        return isProfileEligible(profile, botFlags, botCharacteristics)
    }

    /**
     * Creates evaluation context from bot state for condition checking
     */
    fun createEvaluationContext(bot: content.bot.Bot): Map<String, Any> {
        val player = bot.player

        // Build inventory map with item counts
        val inventoryMap = mutableMapOf<String, Int>()
        for (item in player.inventory.items) {
            if (!item.isEmpty()) {
                val currentCount = inventoryMap[item.id] ?: 0
                inventoryMap[item.id] = currentCount + item.amount
            }
        }

        // Build bank map with item counts
        val bankMap = mutableMapOf<String, Int>()
        for (item in player.bank.items) {
            if (!item.isEmpty()) {
                val currentCount = bankMap[item.id] ?: 0
                bankMap[item.id] = currentCount + item.amount
            }
        }

        // Build skill levels map
        val skillLevels = mutableMapOf<String, Int>()
        for (skill in world.gregs.voidps.engine.entity.character.player.skill.Skill.all) {
            skillLevels[skill.name.lowercase()] = player.levels.getMax(skill)
        }

        // Build equipment list
        val equipmentList = mutableListOf<String>()
        for (item in player.equipment.items) {
            if (!item.isEmpty()) {
                equipmentList.add(item.id)
            }
        }

        // Get completed quests
        val completedQuests = mutableSetOf<String>()
        // TODO: Add quest completion checking based on game state

        // Get current area
        val currentArea = "" // TODO: Add area detection based on player.tile

        return mapOf(
            "inventory" to inventoryMap,
            "bank" to bankMap,
            "skill_levels" to skillLevels,
            "flags" to bot.getBotFlags().getFlagNames(),
            "equipment" to equipmentList,
            "combat_level" to player.combatLevel,
            "total_level" to calculateTotalLevel(player),
            "inventory_count" to player.inventory.count,
            "completed_quests" to completedQuests,
            "area" to currentArea
        )
    }
}

/**
 * Statistics about loaded profiles
 */
data class ProfileStats(
    val totalProfiles: Int,
    val categoryCounts: Map<String, Int>,
    val profilesWithFlags: Int,
    val profilesWithSteps: Int,
)

/**
 * Bot characteristics used for profile assignment evaluation
 */
data class BotCharacteristics(
    val combatLevel: Int,
    val totalLevel: Int,
    val location: world.gregs.voidps.type.Tile,
    val hasWeapon: Boolean,
    val hasArmor: Boolean,
    val inventoryCount: Int,
    val highestSkillLevel: Int,
    val questCount: Int,
)

/**
 * Result of step execution with fallback handling
 */
sealed class StepExecutionResult {
    object InProgress : StepExecutionResult()
    data class Completed(val awardedFlags: List<String>) : StepExecutionResult()
    data class Failed(val reason: String) : StepExecutionResult()
    data class FallbackAssigned(val newProfile: BotProfile) : StepExecutionResult()
}
