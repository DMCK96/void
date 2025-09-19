package content.bot.profile

import it.unimi.dsi.fastutil.objects.ObjectArrayList
import world.gregs.config.Config
import world.gregs.voidps.engine.data.ConfigFiles
import world.gregs.voidps.engine.data.find
import world.gregs.voidps.engine.data.list
import world.gregs.voidps.engine.inject
import java.io.File

/**
 * Represents a hierarchical bot profile loaded from TOML configuration files
 */
data class BotProfile(
    val name: String,
    val description: String = "",
    val category: String = "",
    val weight: Int = 1,
    val required_flags: List<String> = emptyList(),
    val steps: List<BotStep> = emptyList(),
) {
    companion object {
        private val configFiles: ConfigFiles by inject()

        /**
         * Loads a bot profile from a TOML file using the existing Config system
         */
        fun load(fileName: String): BotProfile {
            val filePath = configFiles.find("bot/profiles/$fileName")
            return load(File(filePath))
        }

        /**
         * Loads a bot profile from a File using the Config.fileReader pattern
         */
        fun load(file: File): BotProfile {
            var name = ""
            var description = ""
            var category = ""
            var weight = 1
            val requiredFlags = ObjectArrayList<String>()
            val steps = ObjectArrayList<BotStep>()

            Config.fileReader(file) {
                // Read top-level properties
                while (nextPair()) {
                    when (val key = key()) {
                        "name" -> name = string()
                        "description" -> description = string()
                        "category" -> category = string()
                        "weight" -> weight = int()
                        "required_flags" -> {
                            while (nextElement()) {
                                requiredFlags.add(string())
                            }
                        }
                        else -> throw IllegalArgumentException("Unexpected key: '$key' ${exception()}")
                    }
                }

                // Read step sections
                while (nextSection()) {
                    val sectionName = section()
                    if (sectionName.startsWith("step.")) {
                        val stepName = sectionName.removePrefix("step.")
                        var stepDescription = ""
                        val requirements = ObjectArrayList<String>()
                        val completionCriteria = ObjectArrayList<String>()
                        val awardFlags = ObjectArrayList<String>()
                        var fallbackCategory = ""

                        while (nextPair()) {
                            when (val stepKey = key()) {
                                "description" -> stepDescription = string()
                                "requirements" -> {
                                    while (nextElement()) {
                                        requirements.add(string())
                                    }
                                }
                                "completion_criteria" -> {
                                    while (nextElement()) {
                                        completionCriteria.add(string())
                                    }
                                }
                                "award_flags" -> {
                                    while (nextElement()) {
                                        awardFlags.add(string())
                                    }
                                }
                                "fallback_category" -> fallbackCategory = string()
                                else -> throw IllegalArgumentException("Unexpected step key: '$stepKey' in step '$stepName' ${exception()}")
                            }
                        }

                        steps.add(
                            BotStep(
                                name = stepName,
                                description = stepDescription,
                                requirements = requirements,
                                completion_criteria = completionCriteria,
                                award_flags = awardFlags,
                                fallback_category = fallbackCategory,
                            ),
                        )
                    } else {
                        throw IllegalArgumentException("Unexpected section: '$sectionName' ${exception()}")
                    }
                }
            }

            return BotProfile(
                name = name,
                description = description,
                category = category,
                weight = weight,
                required_flags = requiredFlags,
                steps = steps,
            )
        }

        /**
         * Loads all bot profiles from the profiles directory
         */
        fun loadAll(): List<BotProfile> {
            val profiles = ObjectArrayList<BotProfile>()
            val tomlFiles: List<String> = configFiles.list("toml")
            val profileFiles = tomlFiles.filter { filePath: String -> filePath.contains("bot/profiles/") }

            profileFiles.forEach { filePath: String ->
                try {
                    profiles.add(load(File(filePath)))
                } catch (e: Exception) {
                    println("Failed to load bot profile from $filePath: ${e.message}")
                }
            }

            return profiles
        }

        /**
         * Finds profiles that match the given category and player state
         */
        fun findMatchingProfiles(
            category: String,
            playerFlags: Set<String> = emptySet(),
        ): List<BotProfile> = loadAll().filter { profile ->
            (profile.category == category || category.isEmpty()) &&
                profile.required_flags.all { flag -> playerFlags.contains(flag) }
        }.sortedByDescending { it.weight }
    }
}
