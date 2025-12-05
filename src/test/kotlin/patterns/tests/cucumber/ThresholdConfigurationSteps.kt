package patterns.tests.cucumber

import io.cucumber.java.en.Given
import io.cucumber.java.en.When
import io.cucumber.java.en.Then
import io.cucumber.java.Before
import io.cucumber.datatable.DataTable
import patterns.dimensional.thresholds.ThresholdService
import patterns.dimensional.thresholds.DangerLevel
import patterns.dimensional.thresholds.ModerationLevel
import patterns.dimensional.thresholds.ThresholdConfig
import patterns.dimensional.DimensionType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assertions.fail

/**
 * Cucumber Step Definitions for Threshold Configuration BDD Tests
 */
class ThresholdConfigurationSteps {

    private lateinit var thresholdService: ThresholdService
    private var queryResult: Any? = null
    private var booleanResult: Boolean = false
    private var effectiveConfig: ThresholdConfig? = null
    private var currentCharacterCount: Int = 0

    // ==========================================
    // Setup
    // ==========================================

    @Before
    fun setup() {
        thresholdService = ThresholdService()
    }

    @Given("the threshold service is initialized with default settings")
    fun initializeThresholdService() {
        // Already initialized in @Before
        assertNotNull(thresholdService)
    }

    @Given("the following world types exist:")
    fun configureWorldTypes(dataTable: DataTable) {
        // World types are pre-configured in ThresholdService.init()
        // This step documents the expected configuration
        dataTable.asMaps().forEach { row ->
            val worldType = row["worldType"]!!
            // Verify configuration exists
            assertNotNull(worldType)
        }
    }

    // ==========================================
    // World/Region/Location Setup
    // ==========================================

    @Given("a world {string} of type {string}")
    fun createWorld(worldId: String, worldType: String) {
        thresholdService.registerWorld(worldId, worldType)
    }

    @Given("a region {string} in world {string}")
    fun createRegion(regionId: String, worldId: String) {
        thresholdService.registerRegion(worldId, regionId)
    }

    @Given("a location {string} in region {string}")
    fun createLocation(locationId: String, regionId: String) {
        thresholdService.registerLocation(regionId, locationId)
    }

    // ==========================================
    // Threshold Configuration
    // ==========================================

    @Given("the region {string} has threshold {string} set to {string}")
    fun setRegionThreshold(regionId: String, key: String, value: String) {
        val parsedValue = parseThresholdValue(key, value)
        thresholdService.setThreshold(DimensionType.REGION, regionId, key, parsedValue)
    }

    @Given("the world {string} has threshold {string} set to {string}")
    fun setWorldThreshold(worldId: String, key: String, value: String) {
        val parsedValue = parseThresholdValue(key, value)
        thresholdService.setThreshold(DimensionType.WORLD, worldId, key, parsedValue)
    }

    @Given("the location {string} has threshold {string} set to {int}")
    fun setLocationThresholdInt(locationId: String, key: String, value: Int) {
        thresholdService.setThreshold(DimensionType.LOCATION, locationId, key, value)
    }

    @Given("the location {string} currently has {int} characters")
    fun setCurrentCharacterCount(locationId: String, count: Int) {
        currentCharacterCount = count
    }

    @When("the location {string} has {int} characters")
    fun updateCharacterCount(locationId: String, count: Int) {
        currentCharacterCount = count
    }

    // ==========================================
    // Query Operations
    // ==========================================

    @When("I query thresholds for location {string}")
    fun queryThresholds(locationId: String) {
        effectiveConfig = thresholdService.getEffectiveThresholds(locationId)
    }

    @When("I request effective thresholds for {string}")
    fun requestEffectiveThresholds(locationId: String) {
        effectiveConfig = thresholdService.getEffectiveThresholds(locationId)
    }

    @When("an AI response has confidence {double}")
    fun setAIConfidence(confidence: Double) {
        queryResult = confidence
    }

    @When("a random value of {double} is generated")
    fun setRandomValue(value: Double) {
        queryResult = value
    }

    @When("I check if another character can be added to {string}")
    fun checkCanAddCharacter(locationId: String) {
        booleanResult = thresholdService.canAddCharacterToScene(locationId, currentCharacterCount)
    }

    @When("I check if another character can be added to {string} with {int} existing")
    fun checkCanAddCharacterWithCount(locationId: String, existingCount: Int) {
        booleanResult = thresholdService.canAddCharacterToScene(locationId, existingCount)
    }

    // ==========================================
    // Assertions
    // ==========================================

    @Then("the threshold {string} should be {int}")
    fun verifyIntThreshold(key: String, expected: Int) {
        assertNotNull(effectiveConfig, "Effective config should not be null")
        when (key) {
            "maxCharactersPerScene" -> assertEquals(expected, effectiveConfig!!.maxCharactersPerScene, "Threshold '$key' mismatch")
            else -> fail("Unknown int threshold key: $key")
        }
    }

    @Then("the threshold {string} should be {string}")
    fun verifyStringThreshold(key: String, expected: String) {
        assertNotNull(effectiveConfig, "Effective config should not be null")
        when (key) {
            "dangerLevel" -> assertEquals(expected, effectiveConfig!!.dangerLevel.name, "Threshold '$key' mismatch")
            "contentModerationLevel" -> assertEquals(expected, effectiveConfig!!.contentModerationLevel.name, "Threshold '$key' mismatch")
            else -> fail("Unknown string threshold key: $key")
        }
    }

    @Then("the response should meet the confidence threshold for {string}")
    fun verifyMeetsConfidence(locationId: String) {
        val confidence = queryResult as Double
        assertTrue(
            thresholdService.meetsConfidenceThreshold(locationId, confidence),
            "Expected confidence $confidence to meet threshold"
        )
    }

    @Then("the response should not meet the confidence threshold for {string}")
    fun verifyDoesNotMeetConfidence(locationId: String) {
        val confidence = queryResult as Double
        assertFalse(
            thresholdService.meetsConfidenceThreshold(locationId, confidence),
            "Expected confidence $confidence to NOT meet threshold"
        )
    }

    @Then("the result should be true")
    fun verifyResultTrue() {
        assertTrue(booleanResult, "Expected result to be true")
    }

    @Then("the result should be false")
    fun verifyResultFalse() {
        assertFalse(booleanResult, "Expected result to be false")
    }

    @Then("a random event should trigger at {string}")
    fun verifyEventTriggers(locationId: String) {
        val randomValue = queryResult as Double
        assertTrue(
            thresholdService.shouldTriggerEvent(locationId, randomValue),
            "Expected event to trigger at $locationId with random value $randomValue"
        )
    }

    @Then("a random event should not trigger at {string}")
    fun verifyEventDoesNotTrigger(locationId: String) {
        val randomValue = queryResult as Double
        assertFalse(
            thresholdService.shouldTriggerEvent(locationId, randomValue),
            "Expected event NOT to trigger at $locationId with random value $randomValue"
        )
    }

    @Then("the effective configuration should contain:")
    fun verifyEffectiveConfiguration(dataTable: DataTable) {
        assertNotNull(effectiveConfig, "Effective config should not be null")

        dataTable.asMaps().forEach { row ->
            val key = row["key"]!!
            val expectedValue = row["value"]!!

            when (key) {
                "minConfidence" -> assertEquals(expectedValue.toDouble(), effectiveConfig!!.minConfidence, 0.001)
                "maxCharactersPerScene" -> assertEquals(expectedValue.toInt(), effectiveConfig!!.maxCharactersPerScene)
                "dangerLevel" -> assertEquals(expectedValue, effectiveConfig!!.dangerLevel.name)
                "eventTriggerProbability" -> assertEquals(expectedValue.toDouble(), effectiveConfig!!.eventTriggerProbability, 0.001)
                "contentModerationLevel" -> assertEquals(expectedValue, effectiveConfig!!.contentModerationLevel.name)
            }
        }
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
}
