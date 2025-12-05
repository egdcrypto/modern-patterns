package patterns.dimensional.thresholds

import patterns.dimensional.Dimension
import patterns.dimensional.DimensionType
import patterns.dimensional.DimensionalConfigurationGraph

/**
 * Threshold Configuration for Narrative Engine
 *
 * Manages configurable thresholds at different dimensional levels:
 * - GLOBAL: Default thresholds for all worlds
 * - WORLD_TYPE: Thresholds by genre (Fantasy, Sci-Fi, etc.)
 * - WORLD: Specific world overrides
 * - REGION: Regional variations
 * - LOCATION: Location-specific settings
 *
 * Use cases:
 * - AI response confidence thresholds
 * - Character interaction limits
 * - Event trigger thresholds
 * - Content moderation levels
 */

// ============================================
// Threshold Types
// ============================================

data class ThresholdConfig(
    val minConfidence: Double,         // Minimum AI confidence to accept response
    val maxCharactersPerScene: Int,    // Max NPCs in a scene
    val interactionCooldownMs: Long,   // Cooldown between character interactions
    val eventTriggerProbability: Double, // Probability of random events
    val dangerLevel: DangerLevel,      // Affects combat/threat mechanics
    val contentModerationLevel: ModerationLevel
)

enum class DangerLevel { SAFE, LOW, MEDIUM, HIGH, EXTREME }
enum class ModerationLevel { STRICT, STANDARD, RELAXED }

// ============================================
// Threshold Service
// ============================================

class ThresholdService {

    private val thresholdGraph = DimensionalConfigurationGraph<Any>()

    init {
        initializeDefaultHierarchy()
    }

    /**
     * Initialize default threshold hierarchy.
     */
    private fun initializeDefaultHierarchy() {
        val global = Dimension(DimensionType.GLOBAL, "default")

        // World types
        val fantasy = Dimension(DimensionType.WORLD_TYPE, "FANTASY")
        val sciFi = Dimension(DimensionType.WORLD_TYPE, "SCI_FI")
        val horror = Dimension(DimensionType.WORLD_TYPE, "HORROR")

        // Build hierarchy
        thresholdGraph.addHierarchy(global, fantasy)
        thresholdGraph.addHierarchy(global, sciFi)
        thresholdGraph.addHierarchy(global, horror)

        // Set global defaults
        thresholdGraph.setConfiguration(global, "minConfidence", 0.7)
        thresholdGraph.setConfiguration(global, "maxCharactersPerScene", 10)
        thresholdGraph.setConfiguration(global, "interactionCooldownMs", 5000L)
        thresholdGraph.setConfiguration(global, "eventTriggerProbability", 0.1)
        thresholdGraph.setConfiguration(global, "dangerLevel", DangerLevel.LOW)
        thresholdGraph.setConfiguration(global, "contentModerationLevel", ModerationLevel.STANDARD)

        // Fantasy-specific thresholds
        thresholdGraph.setConfiguration(fantasy, "maxCharactersPerScene", 15)
        thresholdGraph.setConfiguration(fantasy, "eventTriggerProbability", 0.15)
        thresholdGraph.setConfiguration(fantasy, "dangerLevel", DangerLevel.MEDIUM)

        // Horror-specific thresholds
        thresholdGraph.setConfiguration(horror, "minConfidence", 0.8)  // Higher confidence for horror content
        thresholdGraph.setConfiguration(horror, "dangerLevel", DangerLevel.HIGH)
        thresholdGraph.setConfiguration(horror, "eventTriggerProbability", 0.25)

        // Sci-Fi thresholds
        thresholdGraph.setConfiguration(sciFi, "maxCharactersPerScene", 20)
        thresholdGraph.setConfiguration(sciFi, "interactionCooldownMs", 3000L)
    }

    /**
     * Register a new world with its type.
     */
    fun registerWorld(worldId: String, worldType: String) {
        val worldDimension = Dimension(DimensionType.WORLD, worldId)
        val typeDimension = Dimension(DimensionType.WORLD_TYPE, worldType)
        thresholdGraph.addHierarchy(typeDimension, worldDimension)
    }

    /**
     * Register a region within a world.
     */
    fun registerRegion(worldId: String, regionId: String) {
        val worldDimension = Dimension(DimensionType.WORLD, worldId)
        val regionDimension = Dimension(DimensionType.REGION, regionId)
        thresholdGraph.addHierarchy(worldDimension, regionDimension)
    }

    /**
     * Register a location within a region.
     */
    fun registerLocation(regionId: String, locationId: String) {
        val regionDimension = Dimension(DimensionType.REGION, regionId)
        val locationDimension = Dimension(DimensionType.LOCATION, locationId)
        thresholdGraph.addHierarchy(regionDimension, locationDimension)
    }

    /**
     * Set threshold at any dimensional level.
     */
    fun setThreshold(dimensionType: DimensionType, dimensionValue: String, key: String, value: Any) {
        val dimension = Dimension(dimensionType, dimensionValue)
        thresholdGraph.setConfiguration(dimension, key, value)
    }

    /**
     * Get threshold for a specific location.
     */
    fun getThreshold(locationId: String, key: String): Any? {
        val dimension = Dimension(DimensionType.LOCATION, locationId)
        return thresholdGraph.resolve(dimension, key)
    }

    /**
     * Get all effective thresholds for a location.
     */
    fun getEffectiveThresholds(locationId: String): ThresholdConfig {
        val dimension = Dimension(DimensionType.LOCATION, locationId)
        val config = thresholdGraph.getEffectiveConfiguration(dimension)

        return ThresholdConfig(
            minConfidence = config["minConfidence"] as? Double ?: 0.7,
            maxCharactersPerScene = config["maxCharactersPerScene"] as? Int ?: 10,
            interactionCooldownMs = config["interactionCooldownMs"] as? Long ?: 5000L,
            eventTriggerProbability = config["eventTriggerProbability"] as? Double ?: 0.1,
            dangerLevel = config["dangerLevel"] as? DangerLevel ?: DangerLevel.LOW,
            contentModerationLevel = config["contentModerationLevel"] as? ModerationLevel ?: ModerationLevel.STANDARD
        )
    }

    /**
     * Check if AI response meets confidence threshold.
     */
    fun meetsConfidenceThreshold(locationId: String, confidence: Double): Boolean {
        val threshold = getThreshold(locationId, "minConfidence") as? Double ?: 0.7
        return confidence >= threshold
    }

    /**
     * Check if scene can accept more characters.
     */
    fun canAddCharacterToScene(locationId: String, currentCount: Int): Boolean {
        val max = getThreshold(locationId, "maxCharactersPerScene") as? Int ?: 10
        return currentCount < max
    }

    /**
     * Check if random event should trigger.
     */
    fun shouldTriggerEvent(locationId: String, randomValue: Double): Boolean {
        val probability = getThreshold(locationId, "eventTriggerProbability") as? Double ?: 0.1
        return randomValue < probability
    }
}
