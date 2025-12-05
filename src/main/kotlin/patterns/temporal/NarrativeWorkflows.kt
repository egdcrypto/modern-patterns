package patterns.temporal

import java.time.Duration

/**
 * Temporal.io Workflow Orchestration Patterns
 *
 * Workflows are durable, fault-tolerant orchestrations that survive failures.
 * Activities are the actual work units that can fail and be retried.
 *
 * Based on MMORPG Narrative Engine document processing pipeline.
 */

// ============================================
// Workflow Interface
// ============================================

/**
 * Document Processing Workflow
 *
 * Orchestrates the pipeline:
 * 1. Parse document (PDF, DOCX, etc.)
 * 2. Extract entities (characters, locations, events)
 * 3. Detect relationships between entities
 * 4. Generate character profiles with AI
 * 5. Create world from extracted data
 */
interface DocumentProcessingWorkflow {
    /**
     * Process a narrative document and create a world.
     *
     * @param documentId The uploaded document ID
     * @param ownerId The user who owns the world
     * @return The created world ID
     */
    fun processDocument(documentId: String, ownerId: String): String
}

// ============================================
// Activity Interfaces
// ============================================

/**
 * Document parsing activities.
 */
interface DocumentParsingActivities {
    fun parseDocument(documentId: String): ParsedDocument
    fun extractText(documentId: String): String
}

/**
 * Entity extraction activities (AI-powered).
 */
interface EntityExtractionActivities {
    fun extractCharacters(text: String): List<ExtractedCharacter>
    fun extractLocations(text: String): List<ExtractedLocation>
    fun extractEvents(text: String): List<ExtractedEvent>
    fun detectRelationships(entities: List<ExtractedEntity>): List<Relationship>
}

/**
 * World creation activities.
 */
interface WorldCreationActivities {
    fun createWorld(name: String, description: String, ownerId: String): String
    fun addCharacterToWorld(worldId: String, character: ExtractedCharacter): String
    fun addLocationToWorld(worldId: String, location: ExtractedLocation): String
}

// ============================================
// Workflow Implementation
// ============================================

/**
 * Workflow implementation with saga pattern for compensation.
 */
class DocumentProcessingWorkflowImpl(
    private val parsingActivities: DocumentParsingActivities,
    private val extractionActivities: EntityExtractionActivities,
    private val worldActivities: WorldCreationActivities
) : DocumentProcessingWorkflow {

    override fun processDocument(documentId: String, ownerId: String): String {
        // Step 1: Parse document
        val parsedDoc = parsingActivities.parseDocument(documentId)
        val text = parsingActivities.extractText(documentId)

        // Step 2: Extract entities (can run in parallel)
        val characters = extractionActivities.extractCharacters(text)
        val locations = extractionActivities.extractLocations(text)
        val events = extractionActivities.extractEvents(text)

        // Step 3: Detect relationships
        val allEntities: List<ExtractedEntity> = characters + locations + events
        val relationships = extractionActivities.detectRelationships(allEntities)

        // Step 4: Create world (saga - compensate on failure)
        val worldId = worldActivities.createWorld(
            name = parsedDoc.title,
            description = "World created from: ${parsedDoc.title}",
            ownerId = ownerId
        )

        // Step 5: Add entities to world
        try {
            characters.forEach { character ->
                worldActivities.addCharacterToWorld(worldId, character)
            }
            locations.forEach { location ->
                worldActivities.addLocationToWorld(worldId, location)
            }
        } catch (e: Exception) {
            // Saga compensation: delete world if entity creation fails
            // worldActivities.deleteWorld(worldId)
            throw e
        }

        return worldId
    }
}

// ============================================
// Data Classes
// ============================================

data class ParsedDocument(
    val id: String,
    val title: String,
    val format: String,
    val pageCount: Int,
    val wordCount: Int
)

sealed interface ExtractedEntity {
    val id: String
    val name: String
    val confidence: Double
}

data class ExtractedCharacter(
    override val id: String,
    override val name: String,
    override val confidence: Double,
    val role: String,           // protagonist, antagonist, supporting
    val traits: List<String>,
    val description: String
) : ExtractedEntity

data class ExtractedLocation(
    override val id: String,
    override val name: String,
    override val confidence: Double,
    val type: String,           // city, forest, castle, etc.
    val description: String
) : ExtractedEntity

data class ExtractedEvent(
    override val id: String,
    override val name: String,
    override val confidence: Double,
    val type: String,           // battle, meeting, discovery
    val participants: List<String>,
    val description: String
) : ExtractedEntity

data class Relationship(
    val sourceId: String,
    val targetId: String,
    val type: String,           // ally, enemy, family, romantic
    val confidence: Double,
    val description: String
)

// ============================================
// Activity Options (Retry Configuration)
// ============================================

/**
 * Activity retry configuration.
 * In actual Temporal code, these would be ActivityOptions.
 */
object ActivityConfigurations {

    // Fast activities with quick retries
    val parsingOptions = ActivityConfig(
        startToCloseTimeout = Duration.ofMinutes(5),
        retryInitialInterval = Duration.ofSeconds(1),
        retryMaximumAttempts = 3,
        retryBackoffCoefficient = 2.0
    )

    // AI activities - longer timeout, more retries
    val extractionOptions = ActivityConfig(
        startToCloseTimeout = Duration.ofMinutes(30),
        retryInitialInterval = Duration.ofSeconds(5),
        retryMaximumAttempts = 5,
        retryBackoffCoefficient = 2.0
    )

    // Database activities - shorter timeout
    val persistenceOptions = ActivityConfig(
        startToCloseTimeout = Duration.ofMinutes(2),
        retryInitialInterval = Duration.ofSeconds(1),
        retryMaximumAttempts = 3,
        retryBackoffCoefficient = 2.0
    )
}

data class ActivityConfig(
    val startToCloseTimeout: Duration,
    val retryInitialInterval: Duration,
    val retryMaximumAttempts: Int,
    val retryBackoffCoefficient: Double
)
