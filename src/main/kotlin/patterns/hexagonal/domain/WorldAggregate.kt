package patterns.hexagonal.domain

import java.time.Instant
import java.util.UUID

/**
 * Hexagonal Architecture - Domain Layer
 *
 * Pure domain logic with no infrastructure dependencies.
 * Based on MMORPG Narrative Engine - World Management bounded context.
 */

// ============================================
// Value Objects
// ============================================

@JvmInline
value class WorldId(val value: String) {
    companion object {
        fun generate() = WorldId(UUID.randomUUID().toString())
    }
}

@JvmInline
value class CharacterId(val value: String) {
    companion object {
        fun generate() = CharacterId(UUID.randomUUID().toString())
    }
}

data class Personality(
    val personalityType: String,      // e.g., "ENFP"
    val dominantTrait: String,        // e.g., "extroversion"
    val emotionalStability: Double,   // 0.0 - 1.0
    val socialAffinity: Double        // 0.0 - 1.0
)

data class CharacterAttributes(
    val strength: Int,
    val intelligence: Int,
    val charisma: Int,
    val wisdom: Int,
    val dexterity: Int,
    val constitution: Int
)

data class Coordinates(
    val x: Double,
    val y: Double,
    val z: Double = 0.0
)

// ============================================
// Domain Enums
// ============================================

enum class WorldStatus { DRAFT, ACTIVE, ARCHIVED, SUSPENDED }
enum class WorldType { FANTASY, SCI_FI, HISTORICAL, CONTEMPORARY, HORROR }

// ============================================
// Commands
// ============================================

data class AddCharacterCommand(
    val name: String,
    val bio: String,
    val personality: Personality,
    val attributes: CharacterAttributes
) {
    init {
        require(name.isNotBlank()) { "Character name cannot be blank" }
    }
}

// ============================================
// Domain Events
// ============================================

/**
 * Base interface for all domain events.
 * Generic ID type allows reuse across different aggregates.
 */
sealed interface DomainEvent<ID> {
    val aggregateId: ID
    val occurredAt: Instant
    val version: Long
}

/**
 * World-specific domain events extend the base with WorldId.
 */
sealed interface WorldDomainEvent : DomainEvent<String> {
    override val aggregateId: String
}

data class WorldCreatedEvent(
    override val aggregateId: String,
    val name: String,
    val description: String,
    val worldType: WorldType,
    val ownerId: String,
    override val version: Long,
    override val occurredAt: Instant = Instant.now()
) : WorldDomainEvent

data class WorldCharacterAddedEvent(
    override val aggregateId: String,
    val characterId: String,
    val name: String,
    val bio: String,
    val personality: Personality,
    val attributes: CharacterAttributes,
    override val version: Long,
    override val occurredAt: Instant = Instant.now()
) : WorldDomainEvent

data class WorldActivatedEvent(
    override val aggregateId: String,
    override val version: Long,
    override val occurredAt: Instant = Instant.now()
) : WorldDomainEvent

data class WorldArchivedEvent(
    override val aggregateId: String,
    val reason: String,
    override val version: Long,
    override val occurredAt: Instant = Instant.now()
) : WorldDomainEvent

// ============================================
// Domain Models (Entities within Aggregate)
// ============================================

data class WorldCharacter(
    val id: CharacterId,
    val worldId: WorldId,
    val name: String,
    val bio: String,
    val personality: Personality,
    val attributes: CharacterAttributes,
    val location: Coordinates? = null
)

// ============================================
// Aggregate Root
// ============================================

/**
 * World Aggregate - the consistency boundary for narrative worlds.
 *
 * Manages:
 * - World lifecycle (draft -> active -> archived)
 * - Characters within the world
 * - Business rules for world operations
 *
 * Event sourced - can be rebuilt from event stream.
 */
class World private constructor(
    val id: WorldId,
    private var name: String,
    private var description: String,
    private var worldType: WorldType,
    private var status: WorldStatus,
    private var ownerId: String,
    private val characters: MutableList<WorldCharacter>,
    private var _version: Long
) {
    private val uncommittedEvents = mutableListOf<WorldDomainEvent>()

    // Read-only accessors
    fun getName() = name
    fun getDescription() = description
    fun getWorldType() = worldType
    fun getStatus() = status
    fun getOwnerId() = ownerId
    fun getCharacters(): List<WorldCharacter> = characters.toList()
    fun getVersion() = _version
    fun getUncommittedEvents(): List<WorldDomainEvent> = uncommittedEvents.toList()

    companion object {
        /**
         * Factory method - creates World in DRAFT state.
         */
        fun create(
            name: String,
            description: String,
            worldType: WorldType,
            ownerId: String
        ): World {
            require(name.isNotBlank()) { "World name cannot be blank" }
            require(ownerId.isNotBlank()) { "Owner ID cannot be blank" }

            val world = World(
                id = WorldId.generate(),
                name = name,
                description = description,
                worldType = worldType,
                status = WorldStatus.DRAFT,
                ownerId = ownerId,
                characters = mutableListOf(),
                _version = 0
            )

            world.raise(
                WorldCreatedEvent(
                    aggregateId = world.id.value,
                    name = name,
                    description = description,
                    worldType = worldType,
                    ownerId = ownerId,
                    version = world._version
                )
            )

            return world
        }

        /**
         * Reconstruct from event stream (event sourcing).
         */
        fun fromEvents(id: WorldId, events: List<WorldDomainEvent>): World {
            val world = World(
                id = id,
                name = "",
                description = "",
                worldType = WorldType.FANTASY,
                status = WorldStatus.DRAFT,
                ownerId = "",
                characters = mutableListOf(),
                _version = 0
            )
            events.forEach { world.apply(it) }
            return world
        }
    }

    /**
     * Add a character to the world using command object.
     */
    fun addCharacter(command: AddCharacterCommand): CharacterId {
        require(status != WorldStatus.ARCHIVED) {
            "Cannot add characters to archived world"
        }

        val characterId = CharacterId.generate()

        raise(
            WorldCharacterAddedEvent(
                aggregateId = id.value,
                characterId = characterId.value,
                name = command.name,
                bio = command.bio,
                personality = command.personality,
                attributes = command.attributes,
                version = _version
            )
        )

        return characterId
    }

    /**
     * Add a character to the world (overloaded for convenience).
     */
    fun addCharacter(
        name: String,
        bio: String,
        personality: Personality,
        attributes: CharacterAttributes
    ): CharacterId = addCharacter(
        AddCharacterCommand(
            name = name,
            bio = bio,
            personality = personality,
            attributes = attributes
        )
    )

    /**
     * Activate the world - makes it playable.
     */
    fun activate() {
        require(status == WorldStatus.DRAFT) {
            "Can only activate worlds in DRAFT status, current: $status"
        }
        require(characters.isNotEmpty()) {
            "World must have at least one character before activation"
        }

        raise(WorldActivatedEvent(aggregateId = id.value, version = _version))
    }

    /**
     * Archive the world - soft delete.
     */
    fun archive(reason: String) {
        require(status != WorldStatus.ARCHIVED) {
            "World is already archived"
        }
        require(reason.isNotBlank()) { "Archive reason cannot be blank" }

        raise(WorldArchivedEvent(aggregateId = id.value, reason = reason, version = _version))
    }

    // ========== Event Application ==========

    private fun apply(event: WorldDomainEvent) {
        when (event) {
            is WorldCreatedEvent -> {
                // ... state already set in constructor for new worlds
                // For replay: set state from event
            }

            is WorldCharacterAddedEvent -> {
                characters.add(
                    WorldCharacter(
                        id = CharacterId(event.characterId),
                        worldId = id,
                        name = event.name,
                        bio = event.bio,
                        personality = event.personality,
                        attributes = event.attributes
                    )
                )
                _version++
            }

            is WorldActivatedEvent -> {
                status = WorldStatus.ACTIVE
                _version++
            }

            is WorldArchivedEvent -> {
                status = WorldStatus.ARCHIVED
                _version++
            }
        }
    }

    private fun raise(event: WorldDomainEvent) {
        apply(event)
        uncommittedEvents.add(event)
    }

    fun markEventsAsCommitted() {
        uncommittedEvents.clear()
    }
}
