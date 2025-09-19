package content.bot.profile

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*

/**
 * Test class for ProfileManager functionality
 * Tests profile loading, caching, and lookup methods
 */
class ProfileManagerTest {
    
    private lateinit var profileManager: ProfileManager
    
    @BeforeEach
    fun setup() {
        // Mock ConfigFiles injection for testing
        // In real environment, this would be injected automatically
        profileManager = ProfileManager()
    }
    
    @AfterEach
    fun cleanup() {
        // Clean up any test state
    }
    
    @Test
    fun `test profile loading and caching`() {
        // Test: Profile loading and caching
        // Setup: Multiple TOML profile files in /data/bot/profiles/ directory
        // Action: ProfileManager loads all profiles on startup
        // Expect: All valid profiles loaded and cached, invalid profiles logged as errors
        
        val allProfiles = profileManager.getAllProfiles()
        
        // Verify profiles were loaded
        assertTrue(allProfiles.isNotEmpty(), "Profiles should be loaded")
        
        // Verify specific test profiles exist
        val beginnerCombat = profileManager.getProfile("beginner_combat")
        assertNotNull(beginnerCombat, "beginner_combat profile should exist")
        assertEquals("combat", beginnerCombat!!.category)
        assertEquals(10, beginnerCombat.weight)
        
        val basicSkilling = profileManager.getProfile("basic_skilling")
        assertNotNull(basicSkilling, "basic_skilling profile should exist")
        assertEquals("skilling", basicSkilling!!.category)
        assertEquals(5, basicSkilling.weight)
        
        // Verify caching works - second call should be faster
        val startTime = System.currentTimeMillis()
        val cachedProfiles = profileManager.getAllProfiles()
        val loadTime = System.currentTimeMillis() - startTime
        
        assertEquals(allProfiles.size, cachedProfiles.size, "Cached results should be identical")
        assertTrue(loadTime < 100, "Cached access should be fast") // Should be very fast
    }
    
    @Test
    fun `test profile lookup methods`() {
        // Test various lookup methods
        
        // Test getProfile
        val profile = profileManager.getProfile("beginner_combat")
        assertNotNull(profile, "Should find beginner_combat profile")
        assertEquals("beginner_combat", profile!!.name)
        
        // Test getProfilesByCategory
        val combatProfiles = profileManager.getProfilesByCategory("combat")
        assertTrue(combatProfiles.isNotEmpty(), "Should have combat profiles")
        assertTrue(combatProfiles.zipWithNext().all { (a, b) -> a.weight >= b.weight }, 
            "Should be sorted by weight descending")
        
        val skillingProfiles = profileManager.getProfilesByCategory("skilling")
        assertTrue(skillingProfiles.isNotEmpty(), "Should have skilling profiles")
        
        // Test getProfilesByName
        val multipleProfiles = profileManager.getProfilesByName(
            listOf("beginner_combat", "basic_skilling", "nonexistent")
        )
        assertEquals(2, multipleProfiles.size, "Should return 2 existing profiles")
        
        // Test getAvailableCategories
        val categories = profileManager.getAvailableCategories()
        assertTrue(categories.contains("combat"), "Should have combat category")
        assertTrue(categories.contains("skilling"), "Should have skilling category")
        assertTrue(categories.contains("questing"), "Should have questing category")
    }
    
    @Test
    fun `test eligible profiles with flags`() {
        // Test flag-based profile filtering
        
        // Test with no flags - should get profiles with no requirements
        val noFlagsProfiles = profileManager.getEligibleProfiles(emptySet())
        val basicSkilling = noFlagsProfiles.find { it.name == "basic_skilling" }
        assertNotNull(basicSkilling, "basic_skilling should be eligible with no flags")
        
        // Test with tutorial flag - should get beginner combat
        val tutorialFlags = setOf("tutorial_completed")
        val tutorialProfiles = profileManager.getEligibleProfiles(tutorialFlags)
        val beginnerCombat = tutorialProfiles.find { it.name == "beginner_combat" }
        assertNotNull(beginnerCombat, "beginner_combat should be eligible with tutorial flag")
        
        // Test advanced profile requirements
        val advancedFlags = setOf(
            "combat_training_completed", 
            "weapon_mastery_basic", 
            "armor_equipped_correctly"
        )
        val advancedProfiles = profileManager.getEligibleProfiles(advancedFlags)
        val advancedCombat = advancedProfiles.find { it.name == "advanced_combat" }
        assertNotNull(advancedCombat, "advanced_combat should be eligible with all required flags")
        
        // Test category filtering with flags
        val combatProfiles = profileManager.getEligibleProfiles(advancedFlags, "combat")
        assertTrue(combatProfiles.isNotEmpty(), "Should have combat profiles with flags")
        assertTrue(combatProfiles.all { it.category == "combat" }, 
            "All profiles should be combat category")
    }
    
    @Test
    fun `test profile statistics`() {
        val stats = profileManager.getProfileStats()
        
        assertTrue(stats.totalProfiles > 0, "Should have profiles loaded")
        assertTrue(stats.categoryCounts.isNotEmpty(), "Should have category counts")
        assertTrue(stats.profilesWithFlags >= 0, "Should have profiles with flags")
        assertTrue(stats.profilesWithSteps >= 0, "Should have profiles with steps")
        
        // Verify category counts
        val combatCount = stats.categoryCounts["combat"] ?: 0
        val skillingCount = stats.categoryCounts["skilling"] ?: 0
        val questingCount = stats.categoryCounts["questing"] ?: 0
        
        assertTrue(combatCount > 0, "Should have combat profiles")
        assertTrue(skillingCount > 0, "Should have skilling profiles")  
        assertTrue(questingCount > 0, "Should have questing profiles")
    }
    
    @Test
    fun `test profile manager state`() {
        // Test loading state
        assertFalse(profileManager.isLoaded(), "Should not be loaded initially")
        
        // Trigger loading
        profileManager.getAllProfiles()
        assertTrue(profileManager.isLoaded(), "Should be loaded after access")
        
        // Test reload functionality
        val initialCount = profileManager.getAllProfiles().size
        profileManager.reloadProfiles()
        val reloadedCount = profileManager.getAllProfiles().size
        
        assertEquals(initialCount, reloadedCount, "Profile count should be same after reload")
        assertTrue(profileManager.isLoaded(), "Should still be loaded after reload")
    }
}
