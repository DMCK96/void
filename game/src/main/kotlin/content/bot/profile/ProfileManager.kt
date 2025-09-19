package content.bot.profile

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import world.gregs.voidps.engine.data.ConfigFiles
import world.gregs.voidps.engine.data.list
import world.gregs.voidps.engine.inject
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
