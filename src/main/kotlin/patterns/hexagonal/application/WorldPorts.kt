package patterns.hexagonal.application

import patterns.hexagonal.domain.*

/**
 * Hexagonal Architecture - Application Layer (Ports)
 *
 * Defines interfaces that connect domain to infrastructure.
 * Based on MMORPG Narrative Engine hexagonal structure.
 */

// ============================================
// Commands
// ============================================

data class CreateWorldCommand(
    val name: String,
    val description: String,
    val worldType: WorldType,
    val ownerId: String
)

data class AddCharacterCommand(
    val worldId: String,
    val name: String,
    val bio: String,
    val personality: Personality,
    val attributes: CharacterAttributes
)

data class ActivateWorldCommand(val worldId: String)

data class ArchiveWorldCommand(
    val worldId: String,
    val reason: String
)

// ============================================
// Inbound Ports (Use Cases)
// ============================================

/**
 * Use case for creating a new narrative world.
 */
interface CreateWorldUseCase {
    fun execute(command: CreateWorldCommand): Result<WorldId>
}

/**
 * Use case for adding characters to a world.
 */
interface AddCharacterUseCase {
    fun execute(command: AddCharacterCommand): Result<CharacterId>
}

/**
 * Use case for activating a world.
 */
interface ActivateWorldUseCase {
    fun execute(command: ActivateWorldCommand): Result<Unit>
}

/**
 * Use case for querying worlds.
 */
interface QueryWorldsUseCase {
    fun findById(worldId: String): WorldDto?
    fun findByOwner(ownerId: String): List<WorldDto>
    fun findActive(): List<WorldDto>
}

// ============================================
// Outbound Ports (Infrastructure Abstractions)
// ============================================

/**
 * Repository port for World aggregate persistence.
 */
interface WorldRepositoryPort {
    fun findById(id: WorldId): World?
    fun save(world: World): World
    fun delete(id: WorldId)
}

/**
 * Event publisher port for domain events.
 */
interface EventPublisherPort {
    fun publish(event: WorldDomainEvent)
    fun publishAll(events: List<WorldDomainEvent>)
}

// ============================================
// DTOs (Data Transfer Objects)
// ============================================

data class WorldDto(
    val id: String,
    val name: String,
    val description: String,
    val worldType: WorldType,
    val status: WorldStatus,
    val ownerId: String,
    val characterCount: Int
)

data class CharacterDto(
    val id: String,
    val name: String,
    val bio: String,
    val personalityType: String
)

// ============================================
// Result Type (using Kotlin's built-in Result)
// ============================================

// Using kotlin.Result<T> for error handling
// Alternative: Arrow's Either<Error, Success>
