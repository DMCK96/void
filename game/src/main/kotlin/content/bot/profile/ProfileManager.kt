package content.bot.profile

import content.bot.getBotFlags
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
        
        // Sort profiles within each category by weight (descending)
        categoryMap.forEach { (category, profiles) ->
            profiles.sortByDescending { it.weight }
            profilesByCategory[category] = profiles
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
            profilesWithSteps = allProfilesCache.count { it.steps.isNotEmpty() }
        )
    }
    
    /**
     * Force reload all profiles (useful for development/testing)
     */
    fun reloadProfiles() {
        synchronized(this) {
            profileCache.clear()
            profilesByCategory.clear()
            allProfilesCache.clear()
            profilesLoaded = false
            loadProfiles()
        }
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
            
            // Return highest weighted eligible profile, or null if none found
            val assignedProfile = eligibleProfiles.maxByOrNull { it.weight }
            
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
            questCount = getQuestCount(player)
        )
    }
    
    /**
     * Checks if a profile is eligible based on flag dependencies and characteristics
     */
    private fun isProfileEligible(
        profile: BotProfile, 
        botFlags: Set<String>, 
        characteristics: BotCharacteristics
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
    private fun calculateTotalLevel(player: world.gregs.voidps.engine.entity.character.player.Player): Int {
        return world.gregs.voidps.engine.entity.character.player.skill.Skill.all.sumOf { skill ->
            if (skill == world.gregs.voidps.engine.entity.character.player.skill.Skill.Constitution) {
                player.levels.getMax(skill) / 10
            } else {
                player.levels.getMax(skill)
            }
        }
    }
    
    /**
     * Gets the highest skill level for bot evaluation
     */
    private fun getHighestSkillLevel(player: world.gregs.voidps.engine.entity.character.player.Player): Int {
        return world.gregs.voidps.engine.entity.character.player.skill.Skill.all.maxOf { skill ->
            if (skill == world.gregs.voidps.engine.entity.character.player.skill.Skill.Constitution) {
                player.levels.getMax(skill) / 10
            } else {
                player.levels.getMax(skill)
            }
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
     * Assigns a profile to a bot with fallback handling
     * Tries preferred category first, then falls back to general category
     * 
     * @param bot The bot to assign a profile to
     * @param preferredCategory Primary category to try
     * @return The assigned profile, or null if no profiles are suitable
     */
    fun assignProfileWithFallback(bot: content.bot.Bot, preferredCategory: String): BotProfile? {
        // Try preferred category first
        var profile = assignProfile(bot, preferredCategory)
        
        // Fallback to general category if no match found
        if (profile == null && preferredCategory != "general") {
            profile = assignProfile(bot, "general")
        }
        
        // Final fallback to any category
        if (profile == null) {
            profile = assignProfile(bot, "")
        }
        
        return profile
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
}

/**
 * Statistics about loaded profiles
 */
data class ProfileStats(
    val totalProfiles: Int,
    val categoryCounts: Map<String, Int>,
    val profilesWithFlags: Int,
    val profilesWithSteps: Int
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
    val questCount: Int
)
