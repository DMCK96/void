package content.bot.profile

/**
 * Represents a single step in a bot profile with conditional logic
 */
data class BotStep(
    val name: String,
    val description: String = "",
    val requirements: List<String> = emptyList(),
    val completion_criteria: List<String> = emptyList(),
    val award_flags: List<String> = emptyList(),
    val fallback_category: String = "",
) {
    companion object {
        /**
         * Evaluates simple boolean conditions for requirements and completion criteria
         * Supports: inventory_has(item), bank_has(item), skill_level(skill, level), has_flag(flag), equipment_has(item)
         */
        fun evaluateCondition(condition: String, context: Map<String, Any>): Boolean {
            val trimmed = condition.trim()

            return when {
                trimmed.startsWith("inventory_has(") && trimmed.endsWith(")") -> {
                    val item = trimmed.removePrefix("inventory_has(").removeSuffix(")")
                        .trim('"', '\'')

                    @Suppress("UNCHECKED_CAST")
                    val inventory = context["inventory"] as? Map<String, Int> ?: emptyMap()
                    inventory.containsKey(item) && (inventory[item] ?: 0) > 0
                }

                trimmed.startsWith("bank_has(") && trimmed.endsWith(")") -> {
                    val item = trimmed.removePrefix("bank_has(").removeSuffix(")")
                        .trim('"', '\'')

                    @Suppress("UNCHECKED_CAST")
                    val bank = context["bank"] as? Map<String, Int> ?: emptyMap()
                    bank.containsKey(item) && (bank[item] ?: 0) > 0
                }

                trimmed.startsWith("skill_level(") && trimmed.endsWith(")") -> {
                    val params = trimmed.removePrefix("skill_level(").removeSuffix(")")
                        .split(",").map { it.trim().trim('"', '\'') }
                    if (params.size != 2) return false

                    val skill = params[0]
                    val requiredLevel = params[1].toIntOrNull() ?: return false

                    @Suppress("UNCHECKED_CAST")
                    val levels = context["skill_levels"] as? Map<String, Int> ?: emptyMap()
                    val currentLevel = levels[skill] ?: 1
                    currentLevel >= requiredLevel
                }

                trimmed.startsWith("has_flag(") && trimmed.endsWith(")") -> {
                    val flag = trimmed.removePrefix("has_flag(").removeSuffix(")")
                        .trim('"', '\'')

                    @Suppress("UNCHECKED_CAST")
                    val flags = context["flags"] as? Set<String> ?: emptySet()
                    flags.contains(flag)
                }

                trimmed.startsWith("equipment_has(") && trimmed.endsWith(")") -> {
                    val item = trimmed.removePrefix("equipment_has(").removeSuffix(")")
                        .trim('"', '\'')

                    @Suppress("UNCHECKED_CAST")
                    val equipment = context["equipment"] as? List<String> ?: emptyList()
                    equipment.contains(item)
                }

                else -> false
            }
        }

        /**
         * Evaluates all conditions in a list (AND logic)
         */
        fun evaluateConditions(conditions: List<String>, context: Map<String, Any>): Boolean = conditions.all { evaluateCondition(it, context) }
    }
}
