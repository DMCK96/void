package content.bot.profile

/**
 * Represents bot flags with timestamps for tracking achievements and unlocks
 * Used to persist long-term bot progression across server restarts and profile changes
 */
data class BotFlags(
    private val flags: MutableMap<String, Long> = mutableMapOf()
) {
    
    /**
     * Checks if a flag is present
     */
    fun hasFlag(flag: String): Boolean = flags.containsKey(flag)
    
    /**
     * Adds a flag with current timestamp
     */
    fun addFlag(flag: String) {
        flags[flag] = System.currentTimeMillis()
    }
    
    /**
     * Adds a flag with specific timestamp
     */
    fun addFlag(flag: String, timestamp: Long) {
        flags[flag] = timestamp
    }
    
    /**
     * Removes a flag
     */
    fun removeFlag(flag: String): Boolean = flags.remove(flag) != null
    
    /**
     * Gets timestamp when flag was added, or null if not present
     */
    fun getFlagTimestamp(flag: String): Long? = flags[flag]
    
    /**
     * Gets all flags as a read-only map
     */
    fun getAllFlags(): Map<String, Long> = flags.toMap()
    
    /**
     * Gets all flag names
     */
    fun getFlagNames(): Set<String> = flags.keys.toSet()
    
    /**
     * Clears all flags
     */
    fun clearFlags() = flags.clear()
    
    /**
     * Gets count of flags
     */
    fun size(): Int = flags.size
    
    /**
     * Checks if no flags are present
     */
    fun isEmpty(): Boolean = flags.isEmpty()
    
    companion object {
        /**
         * Creates BotFlags from a map of flag names to timestamps
         */
        fun fromMap(flagMap: Map<String, Long>): BotFlags {
            return BotFlags(flagMap.toMutableMap())
        }
    }
}
