package patterns.hexagonal.infrastructure

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import patterns.dimensional.thresholds.ThresholdService
import patterns.dimensional.thresholds.ThresholdConfig
import patterns.dimensional.thresholds.DangerLevel
import patterns.dimensional.thresholds.ModerationLevel
import patterns.dimensional.DimensionType

/**
 * REST API Adapter for Threshold Configuration
 *
 * Demonstrates hexagonal architecture where the REST controller
 * is an infrastructure adapter that invokes the domain service.
 *
 * This adapter exposes threshold management operations via HTTP,
 * enabling integration testing with Newman/Postman.
 */

// ============================================
// Request/Response DTOs
// ============================================

data class RegisterWorldRequest(
    val worldId: String,
    val worldType: String
)

data class RegisterRegionRequest(
    val worldId: String,
    val regionId: String
)

data class RegisterLocationRequest(
    val regionId: String,
    val locationId: String
)

data class SetThresholdRequest(
    val dimensionType: String,
    val dimensionId: String,
    val key: String,
    val value: String
)

data class ThresholdConfigResponse(
    val locationId: String,
    val minConfidence: Double,
    val maxCharactersPerScene: Int,
    val interactionCooldownMs: Long,
    val eventTriggerProbability: Double,
    val dangerLevel: String,
    val contentModerationLevel: String
)

data class ConfidenceCheckRequest(
    val locationId: String,
    val confidence: Double
)

data class ConfidenceCheckResponse(
    val meetsThreshold: Boolean,
    val confidence: Double,
    val threshold: Double
)

data class CharacterCheckRequest(
    val locationId: String,
    val currentCount: Int
)

data class CharacterCheckResponse(
    val canAddCharacter: Boolean,
    val currentCount: Int,
    val maxAllowed: Int
)

data class EventTriggerRequest(
    val locationId: String,
    val randomValue: Double
)

data class EventTriggerResponse(
    val shouldTrigger: Boolean,
    val randomValue: Double,
    val probability: Double
)

// ============================================
// REST Controller
// ============================================

@RestController
@RequestMapping("/api/v1/thresholds")
class ThresholdRestAdapter(
    private val thresholdService: ThresholdService
) {

    // ==========================================
    // World/Region/Location Registration
    // ==========================================

    @PostMapping("/worlds")
    fun registerWorld(@RequestBody request: RegisterWorldRequest): ResponseEntity<Map<String, String>> {
        thresholdService.registerWorld(request.worldId, request.worldType)
        return ResponseEntity.ok(mapOf(
            "status" to "created",
            "worldId" to request.worldId,
            "worldType" to request.worldType
        ))
    }

    @PostMapping("/regions")
    fun registerRegion(@RequestBody request: RegisterRegionRequest): ResponseEntity<Map<String, String>> {
        thresholdService.registerRegion(request.worldId, request.regionId)
        return ResponseEntity.ok(mapOf(
            "status" to "created",
            "regionId" to request.regionId,
            "worldId" to request.worldId
        ))
    }

    @PostMapping("/locations")
    fun registerLocation(@RequestBody request: RegisterLocationRequest): ResponseEntity<Map<String, String>> {
        thresholdService.registerLocation(request.regionId, request.locationId)
        return ResponseEntity.ok(mapOf(
            "status" to "created",
            "locationId" to request.locationId,
            "regionId" to request.regionId
        ))
    }

    // ==========================================
    // Threshold Configuration
    // ==========================================

    @PostMapping("/configuration")
    fun setThreshold(@RequestBody request: SetThresholdRequest): ResponseEntity<Map<String, Any>> {
        val dimensionType = DimensionType.valueOf(request.dimensionType)
        val parsedValue = parseThresholdValue(request.key, request.value)

        thresholdService.setThreshold(dimensionType, request.dimensionId, request.key, parsedValue)

        return ResponseEntity.ok(mapOf(
            "status" to "updated",
            "dimensionType" to request.dimensionType,
            "dimensionId" to request.dimensionId,
            "key" to request.key,
            "value" to request.value
        ))
    }

    @GetMapping("/locations/{locationId}")
    fun getEffectiveThresholds(@PathVariable locationId: String): ResponseEntity<ThresholdConfigResponse> {
        val config = thresholdService.getEffectiveThresholds(locationId)
        return ResponseEntity.ok(config.toResponse(locationId))
    }

    @GetMapping("/locations/{locationId}/config/{key}")
    fun getThreshold(
        @PathVariable locationId: String,
        @PathVariable key: String
    ): ResponseEntity<Map<String, Any?>> {
        val value = thresholdService.getThreshold(locationId, key)
        return ResponseEntity.ok(mapOf(
            "locationId" to locationId,
            "key" to key,
            "value" to value?.toString()
        ))
    }

    // ==========================================
    // Threshold Validation Endpoints
    // ==========================================

    @PostMapping("/validate/confidence")
    fun checkConfidence(@RequestBody request: ConfidenceCheckRequest): ResponseEntity<ConfidenceCheckResponse> {
        val meetsThreshold = thresholdService.meetsConfidenceThreshold(request.locationId, request.confidence)
        val threshold = thresholdService.getThreshold(request.locationId, "minConfidence") as? Double ?: 0.7

        return ResponseEntity.ok(ConfidenceCheckResponse(
            meetsThreshold = meetsThreshold,
            confidence = request.confidence,
            threshold = threshold
        ))
    }

    @PostMapping("/validate/characters")
    fun checkCharacterLimit(@RequestBody request: CharacterCheckRequest): ResponseEntity<CharacterCheckResponse> {
        val canAdd = thresholdService.canAddCharacterToScene(request.locationId, request.currentCount)
        val max = thresholdService.getThreshold(request.locationId, "maxCharactersPerScene") as? Int ?: 10

        return ResponseEntity.ok(CharacterCheckResponse(
            canAddCharacter = canAdd,
            currentCount = request.currentCount,
            maxAllowed = max
        ))
    }

    @PostMapping("/validate/event-trigger")
    fun checkEventTrigger(@RequestBody request: EventTriggerRequest): ResponseEntity<EventTriggerResponse> {
        val shouldTrigger = thresholdService.shouldTriggerEvent(request.locationId, request.randomValue)
        val probability = thresholdService.getThreshold(request.locationId, "eventTriggerProbability") as? Double ?: 0.1

        return ResponseEntity.ok(EventTriggerResponse(
            shouldTrigger = shouldTrigger,
            randomValue = request.randomValue,
            probability = probability
        ))
    }

    // ==========================================
    // Helpers
    // ==========================================

    private fun parseThresholdValue(key: String, value: String): Any {
        return when (key) {
            "dangerLevel" -> DangerLevel.valueOf(value)
            "contentModerationLevel" -> ModerationLevel.valueOf(value)
            "minConfidence", "eventTriggerProbability" -> value.toDouble()
            "maxCharactersPerScene" -> value.toInt()
            "interactionCooldownMs" -> value.toLong()
            else -> value
        }
    }

    private fun ThresholdConfig.toResponse(locationId: String) = ThresholdConfigResponse(
        locationId = locationId,
        minConfidence = minConfidence,
        maxCharactersPerScene = maxCharactersPerScene,
        interactionCooldownMs = interactionCooldownMs,
        eventTriggerProbability = eventTriggerProbability,
        dangerLevel = dangerLevel.name,
        contentModerationLevel = contentModerationLevel.name
    )
}

// ============================================
// Spring Configuration for In-Memory Service
// ============================================

/**
 * Configuration that provides an in-memory ThresholdService.
 * In production, this would wire up actual persistence.
 */
@org.springframework.context.annotation.Configuration
class ThresholdConfiguration {

    @org.springframework.context.annotation.Bean
    fun thresholdService(): ThresholdService = ThresholdService()
}
