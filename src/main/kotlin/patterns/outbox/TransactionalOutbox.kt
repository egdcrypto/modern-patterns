package patterns.outbox

import java.time.Instant
import java.util.UUID

/**
 * Transactional Outbox Pattern
 *
 * Ensures reliable event publishing by storing events in the same
 * database transaction as the aggregate state change.
 *
 * A separate processor polls/streams the outbox and publishes to the message broker.
 * This guarantees at-least-once delivery without distributed transactions.
 */

// ============================================
// Outbox Event Model
// ============================================

/**
 * Outbox event stored in the database.
 * Published to message broker by OutboxProcessor.
 */
data class OutboxEvent(
    val id: String = UUID.randomUUID().toString(),
    val aggregateType: String,          // e.g., "World", "Character"
    val aggregateId: String,            // ID of the aggregate
    val eventType: String,              // e.g., "WorldCreated", "CharacterAdded"
    val payload: String,                // JSON serialized event data
    val createdAt: Instant = Instant.now(),
    val processedAt: Instant? = null,   // null = not yet processed
    val status: OutboxStatus = OutboxStatus.PENDING,
    val retryCount: Int = 0,
    val lastError: String? = null
) {
    companion object {
        fun <T> from(
            aggregateType: String,
            aggregateId: String,
            eventType: String,
            event: T,
            serializer: (T) -> String
        ): OutboxEvent {
            return OutboxEvent(
                aggregateType = aggregateType,
                aggregateId = aggregateId,
                eventType = eventType,
                payload = serializer(event)
            )
        }
    }
}

enum class OutboxStatus {
    PENDING,    // Waiting to be processed
    PROCESSED,  // Successfully published
    FAILED,     // Failed after max retries
    DEAD_LETTER // Moved to DLQ for manual review
}

// ============================================
// Outbox Repository
// ============================================

interface OutboxRepository {
    fun save(event: OutboxEvent): OutboxEvent
    fun saveAll(events: List<OutboxEvent>): List<OutboxEvent>
    fun findPendingEvents(limit: Int): List<OutboxEvent>
    fun markAsProcessed(id: String, processedAt: Instant)
    fun markAsFailed(id: String, error: String, retryCount: Int)
    fun moveToDlq(id: String)
}

// ============================================
// Outbox Processor Service
// ============================================

/**
 * Processes outbox events and publishes to message broker.
 *
 * Runs as a scheduled job or uses change streams for real-time processing.
 */
class OutboxProcessorService(
    private val outboxRepository: OutboxRepository,
    private val messagePublisher: MessagePublisher,
    private val maxRetries: Int = 3
) {

    /**
     * Process pending outbox events.
     * Called by scheduler or triggered by change stream.
     */
    fun processPendingEvents() {
        val events = outboxRepository.findPendingEvents(limit = 100)

        events.forEach { event ->
            try {
                // Publish to message broker
                messagePublisher.publish(
                    topic = "${event.aggregateType}.events",
                    key = event.aggregateId,
                    payload = event.payload,
                    headers = mapOf(
                        "eventType" to event.eventType,
                        "aggregateType" to event.aggregateType,
                        "aggregateId" to event.aggregateId,
                        "eventId" to event.id
                    )
                )

                // Mark as processed
                outboxRepository.markAsProcessed(event.id, Instant.now())

            } catch (e: Exception) {
                handleFailure(event, e)
            }
        }
    }

    private fun handleFailure(event: OutboxEvent, error: Exception) {
        val newRetryCount = event.retryCount + 1

        if (newRetryCount >= maxRetries) {
            // Move to dead letter queue
            outboxRepository.moveToDlq(event.id)
            // ... alert operations team
        } else {
            // Mark for retry with exponential backoff
            outboxRepository.markAsFailed(
                id = event.id,
                error = error.message ?: "Unknown error",
                retryCount = newRetryCount
            )
        }
    }
}

// ============================================
// Message Publisher Interface
// ============================================

interface MessagePublisher {
    fun publish(
        topic: String,
        key: String,
        payload: String,
        headers: Map<String, String>
    )
}

// ============================================
// MongoDB Change Stream Processor (Alternative)
// ============================================

/**
 * Real-time outbox processing using MongoDB Change Streams.
 * More responsive than polling but requires replica set.
 */
class ChangeStreamOutboxProcessor(
    private val messagePublisher: MessagePublisher
    // private val mongoTemplate: MongoTemplate
) {

    fun startWatching() {
        // mongoTemplate.getCollection("outbox_events")
        //     .watch(listOf(Aggregates.match(Filters.eq("operationType", "insert"))))
        //     .forEach { change ->
        //         val event = change.fullDocument?.let { parseOutboxEvent(it) }
        //         event?.let { processEvent(it) }
        //     }

        TODO("Implementation uses MongoDB Change Streams")
    }

    private fun processEvent(event: OutboxEvent) {
        messagePublisher.publish(
            topic = "${event.aggregateType}.events",
            key = event.aggregateId,
            payload = event.payload,
            headers = mapOf(
                "eventType" to event.eventType,
                "eventId" to event.id
            )
        )
    }
}
