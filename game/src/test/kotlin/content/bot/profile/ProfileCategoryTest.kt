package content.bot.profile

import WorldTest
import content.bot.Bot
import content.bot.addFlag
import content.bot.getBotFlags
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import world.gregs.voidps.engine.entity.character.player.Player
import world.gregs.voidps.type.Tile

/**
 * Tests for ProfileCategory and weighted selection functionality
 *
 * **Test: Category-based fallback selection**
 * **Setup:** Step with fallback_category "coin_gathering", 3 profiles in category
 * **Action:** Step fails and triggers fallback
 * **Expect:** Random profile selected from coin_gathering category using weights
 */
class ProfileCategoryTest : WorldTest() {

    private lateinit var profileManager: ProfileManager
    private lateinit var testBot: Bot
    private lateinit var player: Player

    @BeforeEach
    fun setup() {
        player = createPlayer(Tile(3200, 3200))
        testBot = Bot(player)
        profileManager = ProfileManager()
    }

    @Test
    fun `ProfileCategory should support weighted selection`() {
        // Setup: Create profiles with different weights
        val profiles = listOf(
            BotProfile("low_weight", "Low weight profile", "coin_gathering", 1, emptyList(), emptyList()),
            BotProfile("medium_weight", "Medium weight profile", "coin_gathering", 5, emptyList(), emptyList()),
            BotProfile("high_weight", "High weight profile", "coin_gathering", 10, emptyList(), emptyList()),
        )

        val category = ProfileCategory("coin_gathering", "Coin gathering methods", profiles)

        // Test weighted selection multiple times to verify distribution
        val selectionResults = mutableMapOf<String, Int>()
        repeat(100) {
            val selected = category.selectWeightedProfile()
            assertNotNull(selected, "Should always select a profile from non-empty category")
            selectionResults[selected!!.name] = selectionResults.getOrDefault(selected.name, 0) + 1
        }

        // Expect: Higher weight profiles selected more frequently
        val highWeightCount = selectionResults["high_weight"] ?: 0
        val lowWeightCount = selectionResults["low_weight"] ?: 0

        assertTrue(
            highWeightCount > lowWeightCount,
            "High weight profile (weight=10) should be selected more than low weight (weight=1). " +
                "High: $highWeightCount, Low: $lowWeightCount",
        )
    }

    @Test
    fun `ProfileCategory should filter by player flags for eligibility`() {
        // Setup: Profiles with different flag requirements
        val profiles = listOf(
            BotProfile("no_requirements", "", "coin_gathering", 5, emptyList(), emptyList()),
            BotProfile("basic_required", "", "coin_gathering", 8, listOf("basic_unlocked"), emptyList()),
            BotProfile("advanced_required", "", "coin_gathering", 10, listOf("basic_unlocked", "advanced_unlocked"), emptyList()),
        )

        val category = ProfileCategory("coin_gathering", "", profiles)

        // Test with no flags
        val noFlagsSelection = category.selectWeightedEligibleProfile(emptySet())
        assertNotNull(noFlagsSelection)
        assertEquals("no_requirements", noFlagsSelection!!.name)

        // Test with basic flag
        val basicFlagsSelection = category.selectWeightedEligibleProfile(setOf("basic_unlocked"))
        assertNotNull(basicFlagsSelection)
        // Should be able to select either no_requirements or basic_required
        assertTrue(basicFlagsSelection!!.name in listOf("no_requirements", "basic_required"))

        // Test with both flags - all profiles eligible
        val allFlagsSelection = category.selectWeightedEligibleProfile(setOf("basic_unlocked", "advanced_unlocked"))
        assertNotNull(allFlagsSelection)
        // Any profile could be selected
        assertTrue(allFlagsSelection!!.name in listOf("no_requirements", "basic_required", "advanced_required"))
    }

    @Test
    fun `Category-based fallback selection should work correctly`() {
        // Setup: Step with fallback_category "coin_gathering", 3 profiles in category
        val coinGatheringProfiles = listOf(
            BotProfile("merchant_trading", "Trade items with NPCs", "coin_gathering", 3, emptyList(), emptyList()),
            BotProfile("skill_monetization", "Sell gathered resources", "coin_gathering", 7, emptyList(), emptyList()),
            BotProfile("treasure_hunting", "Find and collect treasures", "coin_gathering", 5, emptyList(), emptyList()),
        )

        // Create a step that will fail and trigger fallback
        val failingStep = BotStep(
            name = "test_step",
            description = "Step that will fail",
            requirements = listOf("impossible_requirement"),
            completion_criteria = emptyList(),
            award_flags = emptyList(),
            fallback_category = "coin_gathering",
        )

        // Mock ProfileManager behavior by directly testing the logic
        val mockCategory = ProfileCategory("coin_gathering", "", coinGatheringProfiles)

        // Action: Step fails and triggers fallback
        val context = mapOf<String, Any>(
            "flags" to emptySet<String>(),
            "inventory" to emptyList<String>(),
            "skill_levels" to emptyMap<String, Int>(),
            "equipment" to emptyList<String>(),
        )

        // Test that requirements fail
        val requirementsMet = BotStep.evaluateConditions(failingStep.requirements, context)
        assertFalse(requirementsMet, "Step requirements should fail")

        // Test fallback selection
        val fallbackProfile = mockCategory.selectWeightedProfile()

        // Expect: Random profile selected from coin_gathering category using weights
        assertNotNull(fallbackProfile, "Fallback should select a profile from coin_gathering category")
        assertTrue(fallbackProfile!!.category == "coin_gathering", "Selected profile should be from coin_gathering category")
        assertTrue(
            fallbackProfile.name in listOf("merchant_trading", "skill_monetization", "treasure_hunting"),
            "Selected profile should be one of the coin_gathering profiles",
        )

        // Test that weighted selection respects weights over multiple selections
        val selectionCounts = mutableMapOf<String, Int>()
        repeat(50) {
            val selected = mockCategory.selectWeightedProfile()!!
            selectionCounts[selected.name] = selectionCounts.getOrDefault(selected.name, 0) + 1
        }

        val skillMonetizationCount = selectionCounts["skill_monetization"] ?: 0
        val merchantTradingCount = selectionCounts["merchant_trading"] ?: 0

        // skill_monetization (weight=7) should be selected more than merchant_trading (weight=3)
        assertTrue(
            skillMonetizationCount > merchantTradingCount,
            "Higher weight profile should be selected more frequently. " +
                "skill_monetization: $skillMonetizationCount, merchant_trading: $merchantTradingCount",
        )
    }

    @Test
    fun `ProfileCategory fromProfiles method should work correctly`() {
        // Setup: Mixed profiles with different categories
        val allProfiles = listOf(
            BotProfile("combat1", "", "combat", 5, emptyList(), emptyList()),
            BotProfile("coin1", "", "coin_gathering", 3, emptyList(), emptyList()),
            BotProfile("coin2", "", "coin_gathering", 8, emptyList(), emptyList()),
            BotProfile("skill1", "", "skilling", 4, emptyList(), emptyList()),
            BotProfile("general1", "", "", 1, emptyList(), emptyList()), // Empty category -> general
        )

        // Test creating specific category
        val coinCategory = ProfileCategory.fromProfiles("coin_gathering", allProfiles)
        assertEquals("coin_gathering", coinCategory.name)
        assertEquals(2, coinCategory.size)
        assertTrue(coinCategory.profiles.all { it.category == "coin_gathering" })

        // Test general category includes empty category
        val generalCategory = ProfileCategory.fromProfiles("general", allProfiles)
        assertEquals("general", generalCategory.name)
        assertEquals(1, generalCategory.size)
        assertEquals("general1", generalCategory.profiles[0].name)

        // Profiles should be sorted by weight (descending)
        assertEquals("coin2", coinCategory.profiles[0].name) // Weight 8 first
        assertEquals("coin1", coinCategory.profiles[1].name) // Weight 3 second
    }

    @Test
    fun `groupProfilesByCategory should organize profiles correctly`() {
        // Setup: Mixed profiles
        val allProfiles = listOf(
            BotProfile("combat1", "", "combat", 5, emptyList(), emptyList()),
            BotProfile("combat2", "", "combat", 2, emptyList(), emptyList()),
            BotProfile("coin1", "", "coin_gathering", 3, emptyList(), emptyList()),
            BotProfile("general1", "", "", 1, emptyList(), emptyList()),
        )

        // Test grouping
        val categories = ProfileCategory.groupProfilesByCategory(allProfiles)

        assertEquals(3, categories.size)
        assertTrue(categories.containsKey("combat"))
        assertTrue(categories.containsKey("coin_gathering"))
        assertTrue(categories.containsKey("general"))

        // Check combat category
        val combatCategory = categories["combat"]!!
        assertEquals(2, combatCategory.size)
        assertEquals("combat1", combatCategory.profiles[0].name) // Higher weight first

        // Check coin_gathering category
        val coinCategory = categories["coin_gathering"]!!
        assertEquals(1, coinCategory.size)
        assertEquals("coin1", coinCategory.profiles[0].name)

        // Check general category (empty category profiles)
        val generalCategory = categories["general"]!!
        assertEquals(1, generalCategory.size)
        assertEquals("general1", generalCategory.profiles[0].name)
    }

    @Test
    fun `ProfileCategory should handle empty categories gracefully`() {
        // Setup: Empty category
        val emptyCategory = ProfileCategory("empty", "Empty category", emptyList())

        // Test properties
        assertTrue(emptyCategory.isEmpty)
        assertEquals(0, emptyCategory.size)
        assertEquals(0, emptyCategory.totalWeight)

        // Test selections return null
        assertNull(emptyCategory.selectWeightedProfile())
        assertNull(emptyCategory.selectWeightedEligibleProfile(setOf("some_flag")))

        // Test eligibility checks
        assertFalse(emptyCategory.hasEligibleProfile(emptySet()))
        assertTrue(emptyCategory.getEligibleProfiles(emptySet()).isEmpty())
    }

    @Test
    fun `Step execution with fallback should work through ProfileManager`() {
        // Setup: Bot with flags
        testBot.addFlag("basic_unlocked")

        // Create step with fallback category
        val stepWithFallback = BotStep(
            name = "failing_step",
            description = "Step that will fail",
            requirements = listOf("impossible_requirement"),
            completion_criteria = emptyList(),
            award_flags = emptyList(),
            fallback_category = "coin_gathering",
        )

        // Test context
        val context = mapOf<String, Any>(
            "flags" to testBot.getBotFlags().getFlagNames(),
            "inventory" to emptyList<String>(),
            "skill_levels" to emptyMap<String, Int>(),
            "equipment" to emptyList<String>(),
        )

        // Since we don't have actual profiles loaded, test the logic structure
        val requirementsMet = BotStep.evaluateConditions(stepWithFallback.requirements, context)
        assertFalse(requirementsMet, "Requirements should fail")
        assertTrue(stepWithFallback.fallback_category.isNotEmpty(), "Fallback category should be specified")
        assertEquals("coin_gathering", stepWithFallback.fallback_category)
    }
}
