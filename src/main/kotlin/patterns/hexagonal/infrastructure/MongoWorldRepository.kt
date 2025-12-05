package patterns.hexagonal.infrastructure

import patterns.hexagonal.application.WorldRepositoryPort
import patterns.hexagonal.domain.*

/**
 * Infrastructure Layer - MongoDB Repository Adapter
 *
 * Implements the repository port defined in the application layer.
 * This is an "adapter" that connects the domain to MongoDB.
 */
class MongoWorldRepository(
    // private val mongoTemplate: MongoTemplate,
    // private val eventStore: EventStore
) : WorldRepositoryPort {

    override fun findById(id: WorldId): World? {
        // Option 1: Event Sourcing - rebuild from events
        // val events = eventStore.getEvents(id.value)
        // return if (events.isEmpty()) null else World.fromEvents(id, events)

        // Option 2: State-based - load document directly
        // return mongoTemplate.findById(id.value, WorldDocument::class.java)?.toDomain()

        TODO("Implementation uses MongoTemplate or EventStore")
    }

    override fun save(world: World): World {
        // With transactional outbox pattern:
        // 1. Save aggregate state
        // 2. Save events to outbox collection in SAME transaction
        //
        // mongoTemplate.execute { session ->
        //     session.startTransaction()
        //     try {
        //         // Save world document
        //         mongoTemplate.save(world.toDocument())
        //
        //         // Save events to outbox
        //         world.getUncommittedEvents().forEach { event ->
        //             mongoTemplate.save(OutboxEvent.from(event))
        //         }
        //
        //         session.commitTransaction()
        //     } catch (e: Exception) {
        //         session.abortTransaction()
        //         throw e
        //     }
        // }

        TODO("Implementation saves world and events atomically")
    }

    override fun delete(id: WorldId) {
        // Soft delete - archive instead of hard delete
        // mongoTemplate.updateFirst(
        //     Query.query(Criteria.where("_id").is(id.value)),
        //     Update.update("status", "ARCHIVED"),
        //     WorldDocument::class.java
        // )

        TODO("Implementation performs soft delete")
    }
}

/**
 * Document class for MongoDB persistence.
 */
data class WorldDocument(
    val id: String,
    val name: String,
    val description: String,
    val worldType: String,
    val status: String,
    val ownerId: String,
    val characters: List<CharacterDocument>,
    val version: Long
) {
    fun toDomain(): World {
        // ... convert document to domain aggregate
        TODO("Conversion logic")
    }
}

data class CharacterDocument(
    val id: String,
    val name: String,
    val bio: String,
    val personality: Map<String, Any>,
    val attributes: Map<String, Int>
)
