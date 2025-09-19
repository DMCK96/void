package content.bot.profile

import content.entity.death.weightedSample

/**
 * Represents a category of bot profiles with metadata and utility functions
 */
data class ProfileCategory(
    val name: String,
    val description: String = "",
    val profiles: List<BotProfile> = emptyList(),
) {
    /**
     * Gets the total weight of all profiles in this category
     */
    val totalWeight: Int get() = profiles.sumOf { it.weight }

    /**
     * Checks if the category has any profiles
     */
    val isEmpty: Boolean get() = profiles.isEmpty()

    /**
     * Gets the number of profiles in this category
     */
    val size: Int get() = profiles.size

    /**
     * Selects a random profile from this category using weighted distribution
     * Higher weight profiles are more likely to be selected
     *
     * @return A randomly selected profile based on weights, or null if category is empty
     */
    fun selectWeightedProfile(): BotProfile? {
        if (profiles.isEmpty()) return null

        // Convert profiles to weighted pairs for distribution
        val weightedProfiles = profiles.map { it to it.weight }
        return weightedSample(weightedProfiles)
    }

    /**
     * Selects a random profile from eligible profiles using weighted distribution
     * Only considers profiles that match the given flags
     *
     * @param playerFlags Set of flags the player currently has
     * @return A randomly selected eligible profile, or null if none are eligible
     */
    fun selectWeightedEligibleProfile(playerFlags: Set<String>): BotProfile? {
        val eligibleProfiles = profiles.filter { profile ->
            profile.required_flags.all { flag -> playerFlags.contains(flag) }
        }

        if (eligibleProfiles.isEmpty()) return null

        // Convert eligible profiles to weighted pairs for distribution
        val weightedProfiles = eligibleProfiles.map { it to it.weight }
        return weightedSample(weightedProfiles)
    }

    /**
     * Gets profiles sorted by weight (descending)
     */
    fun getProfilesByWeight(): List<BotProfile> = profiles.sortedByDescending { it.weight }

    /**
     * Gets profiles that are eligible based on player flags
     */
    fun getEligibleProfiles(playerFlags: Set<String>): List<BotProfile> = profiles.filter { profile ->
        profile.required_flags.all { flag -> playerFlags.contains(flag) }
    }.sortedByDescending { it.weight }

    /**
     * Checks if any profile in this category is eligible for the given flags
     */
    fun hasEligibleProfile(playerFlags: Set<String>): Boolean = profiles.any { profile ->
        profile.required_flags.all { flag -> playerFlags.contains(flag) }
    }

    companion object {
        /**
         * Creates a ProfileCategory from a list of profiles
         * Filters profiles by the specified category name
         */
        fun fromProfiles(categoryName: String, allProfiles: List<BotProfile>): ProfileCategory {
            val categoryProfiles = allProfiles.filter {
                it.category == categoryName || (categoryName == "general" && it.category.isEmpty())
            }

            return ProfileCategory(
                name = categoryName,
                profiles = categoryProfiles.sortedByDescending { it.weight },
            )
        }

        /**
         * Creates multiple ProfileCategory instances from a list of profiles
         * Groups profiles by their category field
         */
        fun groupProfilesByCategory(allProfiles: List<BotProfile>): Map<String, ProfileCategory> {
            val profilesByCategory = allProfiles.groupBy { profile ->
                profile.category.ifEmpty { "general" }
            }

            return profilesByCategory.mapValues { (categoryName, profiles) ->
                ProfileCategory(
                    name = categoryName,
                    profiles = profiles.sortedByDescending { it.weight },
                )
            }
        }
    }
}
