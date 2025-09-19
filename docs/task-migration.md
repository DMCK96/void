# Task Migration System

This document describes the automatic migration system from the legacy TaskManager to the new ProfileManager system.

## Overview

The Task Migration system provides seamless backward compatibility by automatically migrating existing bots from the current TaskManager system to equivalent profiles without interruption.

## Migration Process

1. **Detection**: On server restart or bot startup, the system detects bots using legacy TaskManager
2. **Mapping**: Legacy task names are mapped to equivalent profile configurations  
3. **Migration**: Bot state is migrated without interrupting current activities
4. **Fallback**: Unmapped tasks continue using the legacy system

## Task to Profile Mappings

### Combat Training Tasks
| Legacy Task Name | Target Profile | Category |
|------------------|----------------|----------|
| "train attack killing goblins" | f2p_combat_basic | combat_training |
| "train defence killing cows" | f2p_combat_basic | combat_training |
| "train strength killing chickens" | f2p_combat_basic | combat_training |
| "combat training lumbridge" | f2p_combat_basic | combat_training |

### Resource Gathering Tasks
| Legacy Task Name | Target Profile | Category |  
|------------------|----------------|----------|
| "cut trees at lumbridge" | woodcutting_basic | resource_gathering |
| "cut willow trees at draynor" | woodcutting_basic | resource_gathering |
| "mine copper ore at lumbridge" | mining_basic | resource_gathering |
| "mine tin ore at lumbridge" | mining_basic | resource_gathering |
| "fish shrimp at draynor" | fishing_basic | resource_gathering |
| "fish anchovies at draynor" | fishing_basic | resource_gathering |
| "cook shrimp at lumbridge" | cooking_basic | resource_gathering |
| "light fires at lumbridge" | firemaking_basic | resource_gathering |

### Skill Training Tasks
| Legacy Task Name | Target Profile | Category |
|------------------|----------------|----------|
| "runecraft air runes" | runecrafting_basic | skill_training |
| "smith bronze items" | smithing_basic | resource_gathering |
| "smelt bronze bars" | smithing_basic | resource_gathering |

### General Tasks  
| Legacy Task Name | Target Profile | Category |
|------------------|----------------|----------|
| "walk randomly" | idle_bot | general |
| "do nothing" | idle_bot | general |

## Pattern Matching

The system uses both exact and partial matching for flexibility:

### Combat Patterns
- Contains "goblin" + "train" → combat_training
- Contains "chicken" + "train" → combat_training  
- Contains "cow" + "train" → combat_training

### Resource Patterns
- Contains "cut" + "tree" → resource_gathering (woodcutting_basic)
- Contains "mine" → resource_gathering (mining_basic)
- Contains "fish" → resource_gathering (fishing_basic)
- Contains "cook" → resource_gathering (cooking_basic)
- Contains "fire" or "light" → resource_gathering (firemaking_basic)
- Contains "smith" → resource_gathering (smithing_basic)
- Contains "smelt" → resource_gathering (smithing_basic)

### Skill Patterns
- Contains "runecraft" → skill_training (runecrafting_basic)

### Idle Patterns
- Contains "walk", "random", or "nothing" → general (idle_bot)

## Migration State Tracking

The system tracks migration state to prevent duplicate migrations:

- `bot_migrated` - Boolean flag indicating bot has been migrated
- `bot_migration_from` - Original task name that was migrated from
- `bot_profile_assigned` - Current profile name assigned to bot
- `last_task_bot` - Preserved for potential fallback scenarios

## Complex Task Exclusions

Tasks matching these patterns are excluded from automatic migration:

- `custom_*` - Custom tasks requiring manual handling
- `complex_*` - Complex tasks with specialized logic
- `advanced_combat_*` - Advanced combat tasks beyond basic profiles
- `specific_location_*` - Location-specific tasks requiring precise handling

## Fallback Behavior

When migration fails or is not applicable:

1. **Legacy Continuation**: Bot continues with existing TaskManager system
2. **Profile System Priority**: New bot assignments prefer ProfileManager
3. **Seamless Operation**: No interruption to bot functionality
4. **Gradual Adoption**: Allows mixed operation during transition

## Implementation Notes

### Key Classes
- `TaskMigration` - Core migration logic and mappings
- `DecisionMaking` - Updated to handle migration detection  
- `ProfileManager` - Target system for migrated bots

### Migration Detection
```kotlin
fun isLegacyBot(bot: Bot): Boolean {
    val player = bot.player
    return player.contains("task_bot") && 
           !player.contains("bot_profile_assigned") && 
           !player.contains("bot_migrated")
}
```

### Profile Assignment Priority
1. Check for legacy bot requiring migration
2. Attempt profile system assignment for new bots
3. Fallback to TaskManager for unmapped cases

## Testing

The migration system includes comprehensive tests:

- **Unit Tests**: TaskMigrationTest.kt - Core mapping logic
- **Integration Tests**: MigrationIntegrationTest.kt - End-to-end flow  
- **End-to-End Tests**: BotMigrationEndToEndTest.kt - Full scenarios

### Test Scenarios Covered

1. **Successful Migration**: "train attack killing goblins" → f2p_combat_basic
2. **Partial Matching**: Various task patterns correctly identified
3. **Complex Task Exclusion**: Custom tasks properly excluded
4. **Already Migrated**: Prevents duplicate migrations
5. **Fallback Behavior**: Legacy system continues for unmapped tasks
6. **State Preservation**: Bot flags and progression maintained

## Success Criteria

✅ Existing bots are automatically migrated to equivalent profiles  
✅ Migration occurs without interrupting current bot activities  
✅ Legacy TaskManager continues to work for unmapped tasks  
✅ Migration state prevents duplicate migrations on subsequent restarts  
✅ Profile system becomes default for new bot assignments  
✅ No functional regression for existing bot behaviors  

## Future Enhancements

- **Analytics**: Track migration success rates and common patterns
- **Rollback**: Ability to revert migrations if needed
- **Advanced Mapping**: Support for more complex task-to-profile relationships
- **Dynamic Updates**: Runtime updates to migration mappings
