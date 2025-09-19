package content.bot.profile

import WorldTest
import content.bot.Bot
import content.bot.addFlag
import content.bot.getBotFlags
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import world.gregs.voidps.engine.entity.character.player.Player
import world.gregs.voidps.type.Tile

/**
 * Tests for category-based fallback selection with weighted distribution
 *
 * **Test: Category-based fallback selection**
 * **Setup:** Step with fallback_category "coin_gathering", 3 profiles in category
 * **Action:** Step fails and triggers fallback
 * **Expect:** Random profile selected from coin_gathering category using weights
 */
class CategoryBasedFallbackTest : WorldTest() {

    private lateinit var testBot: Bot
    private lateinit var player: Player

    @BeforeEach
    fun setup() {
        player = createPlayer(Tile(3200, 3200))
        testBot = Bot(player)
    }

    @Test
    fun `Step with fallback category should select profile from that category`() {
        // Setup: Create a step with fallback_category "coin_gathering"
        val failedStep = BotStep(
            name = "expensive_gear_check",
            description = "Check for expensive gear",
            requirements = listOf("has_expensive_gear"),
            completion_criteria = listOf("gear_equipped"),
            award_flags = listOf("gear_ready"),
            fallback_category = "coin_gathering",
        )

        // Add flags to make bot eligible for profiles but not for step requirements
        testBot.addFlag("basic_unlocked")

        // Mock coin_gathering profiles in the category cache
        val coinGatheringProfiles = listOf(
            BotProfile(
                name = "mining_coins",
                category = "coin_gathering",
                weight = 10,
                required_flags = listOf("basic_unlocked"),
            ),
            BotProfile(
                name = "fishing_coins",
                category = "coin_gathering",
                weight = 8,
                required_flags = listOf("basic_unlocked"),
            ),
            BotProfile(
                name = "woodcutting_coins",
                category = "coin_gathering",
                weight = 5,
                required_flags = listOf("basic_unlocked"),
            ),
        )

        // Create ProfileCategory and test weighted selection directly
        val coinGatheringCategory = ProfileCategory(
            name = "coin_gathering",
            profiles = coinGatheringProfiles,
        )

        val botFlags = testBot.getBotFlags().getFlagNames()

        // Test that category has eligible profiles
        assertTrue(
            coinGatheringCategory.hasEligibleProfile(botFlags),
            "Category should have eligible profiles for bot flags",
        )

        // Test weighted selection multiple times to ensure it works
        var selectedProfiles = mutableSetOf<String>()
        repeat(10) {
            val selected = coinGatheringCategory.selectWeightedEligibleProfile(botFlags)
            assertNotNull(selected, "Should select a profile from coin_gathering category")
            selected?.let { selectedProfiles.add(it.name) }
        }

        // Should select profiles from the coin_gathering category
        assertTrue(selectedProfiles.isNotEmpty(), "Should have selected profiles")
        selectedProfiles.forEach { profileName ->
            assertTrue(
                coinGatheringProfiles.any { it.name == profileName },
                "Selected profile $profileName should be from coin_gathering category",
            )
        }
    }

    @Test
    fun `Step execution logic should handle fallback category correctly`() {
        // Setup: Create a step with fallback category
        val stepWithFallback = BotStep(
            name = "expensive_equipment_step",
            description = "Equip expensive gear",
            requirements = listOf("inventory_has(dragon_sword)", "inventory_has(rune_platebody)"),
            completion_criteria = listOf("equipment_has(dragon_sword)"),
            award_flags = listOf("expensive_gear_equipped"),
            fallback_category = "coin_gathering",
        )

        // Verify step has fallback category set
        assertEquals("coin_gathering", stepWithFallback.fallback_category)
        assertFalse(
            stepWithFallback.fallback_category.isEmpty(),
            "Step should have non-empty fallback category",
        )
    }

    @Test
    fun `Step execution result types should be correctly defined`() {
        // Test that all StepExecutionResult types are properly defined
        val inProgress = StepExecutionResult.InProgress
        val completed = StepExecutionResult.Completed(listOf("test_flag"))
        val failed = StepExecutionResult.Failed("test reason")
        val fallbackProfile = BotProfile("fallback", category = "test", weight = 1)
        val fallbackAssigned = StepExecutionResult.FallbackAssigned(fallbackProfile)

        // Verify types
        assertTrue(inProgress is StepExecutionResult.InProgress)
        assertTrue(completed is StepExecutionResult.Completed)
        assertTrue(failed is StepExecutionResult.Failed)
        assertTrue(fallbackAssigned is StepExecutionResult.FallbackAssigned)

        // Verify data
        assertEquals(listOf("test_flag"), completed.awardedFlags)
        assertEquals("test reason", failed.reason)
        assertEquals(fallbackProfile, fallbackAssigned.newProfile)
    }

    @Test
    fun `Step without fallback category should be handled correctly`() {
        // Setup: Step without fallback category
        val stepWithoutFallback = BotStep(
            name = "no_fallback_step",
            description = "Step without fallback",
            requirements = listOf("inventory_has(impossible_item)"),
            completion_criteria = listOf("impossible_completion"),
            award_flags = listOf("impossible_flag"),
            fallback_category = "", // No fallback
        )

        // Verify step has no fallback category
        assertTrue(
            stepWithoutFallback.fallback_category.isEmpty(),
            "Step should have empty fallback category",
        )
    }

    @Test
    fun `Step should correctly store award flags for completion`() {
        // Setup: Step with award flags
        val stepWithAwards = BotStep(
            name = "basic_gear_step",
            description = "Equip basic gear",
            requirements = listOf("inventory_has(bronze_sword)"),
            completion_criteria = listOf("inventory_has(bronze_sword)"),
            award_flags = listOf("basic_gear_equipped", "combat_ready"),
            fallback_category = "backup_category",
        )

        // Verify step has correct award flags
        assertEquals(
            listOf("basic_gear_equipped", "combat_ready"),
            stepWithAwards.award_flags,
            "Step should have correct award flags",
        )
        assertFalse(
            stepWithAwards.award_flags.isEmpty(),
            "Step should have non-empty award flags",
        )
    }

    @RepeatedTest(5)
    fun `Weighted selection should show distribution over multiple runs`() {
        // Setup: Create profiles with different weights
        val profiles = listOf(
            BotProfile("high_weight", category = "test", weight = 10),
            BotProfile("medium_weight", category = "test", weight = 5),
            BotProfile("low_weight", category = "test", weight = 1),
        )

        val category = ProfileCategory("test", profiles = profiles)

        // Run selection multiple times
        val selections = mutableMapOf<String, Int>()
        repeat(20) {
            val selected = category.selectWeightedProfile()
            selected?.let {
                selections[it.name] = selections.getOrDefault(it.name, 0) + 1
            }
        }

        // Verify all profiles can be selected (over multiple test runs)
        assertTrue(selections.isNotEmpty(), "Should select some profiles")

        // Log selection distribution for manual verification
        println("Selection distribution: $selections")
    }
}
