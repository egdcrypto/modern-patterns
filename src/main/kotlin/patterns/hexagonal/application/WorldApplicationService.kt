package patterns.hexagonal.application

import patterns.hexagonal.domain.*

/**
 * Application Service - Orchestrates domain logic and infrastructure.
 *
 * Following hexagonal architecture:
 * - Implements ingress ports (use case interfaces)
 * - Depends on egress ports (repository, event publisher)
 * - Contains NO business logic (that's in the domain aggregate)
 * - Pure orchestration: load -> execute domain method -> persist -> publish
 */
class WorldApplicationService(
    private val repository: WorldRepositoryPort,
    private val eventPublisher: EventPublisherPort
) : CreateWorldUseCase, AddCharacterUseCase, ActivateWorldUseCase, QueryWorldsUseCase {

    // ========== Create World ==========

    override fun execute(command: CreateWorldCommand): Result<WorldId> {
        return runCatching {
            // 1. Create aggregate (factory method handles validation)
            val world = World.create(
                name = command.name,
                description = command.description,
                worldType = command.worldType,
                ownerId = command.ownerId
            )

            // 2. Persist and publish
            persistAndPublish(world)

            // 3. Return ID
            world.id
        }
    }

    // ========== Add Character ==========

    override fun execute(command: AddCharacterCommand): Result<CharacterId> {
        return runCatching {
            // 1. Load aggregate
            val world = repository.findById(WorldId(command.worldId))
                ?: throw IllegalArgumentException("World not found: ${command.worldId}")

            // 2. Execute domain method (validates state, raises events)
            val characterId = world.addCharacter(
                name = command.name,
                bio = command.bio,
                personality = command.personality,
                attributes = command.attributes
            )

            // 3. Persist and publish
            persistAndPublish(world)

            // 4. Return character ID
            characterId
        }
    }

    // ========== Activate World ==========

    override fun execute(command: ActivateWorldCommand): Result<Unit> {
        return runCatching {
            // 1. Load aggregate
            val world = repository.findById(WorldId(command.worldId))
                ?: throw IllegalArgumentException("World not found: ${command.worldId}")

            // 2. Execute domain method
            world.activate()

            // 3. Persist and publish
            persistAndPublish(world)
        }
    }

    // ========== Query Methods ==========

    override fun findById(worldId: String): WorldDto? {
        return repository.findById(WorldId(worldId))?.toDto()
    }

    override fun findByOwner(ownerId: String): List<WorldDto> {
        // ... implementation would query read model or repository
        TODO("Implementation depends on read model strategy")
    }

    override fun findActive(): List<WorldDto> {
        // ... implementation would query read model
        TODO("Implementation depends on read model strategy")
    }

    // ========== Helper Methods ==========

    /**
     * Standard persist and publish pattern.
     * In production, use transactional outbox for guaranteed delivery.
     */
    private fun persistAndPublish(world: World) {
        // 1. Persist aggregate (with events in same transaction via outbox)
        val savedWorld = repository.save(world)

        // 2. Publish domain events
        val events = savedWorld.getUncommittedEvents()
        eventPublisher.publishAll(events)

        // 3. Mark events as committed
        savedWorld.markEventsAsCommitted()
    }

    private fun World.toDto() = WorldDto(
        id = id.value,
        name = getName(),
        description = getDescription(),
        worldType = getWorldType(),
        status = getStatus(),
        ownerId = getOwnerId(),
        characterCount = getCharacters().size
    )
}
