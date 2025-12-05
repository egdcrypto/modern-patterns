# Architecture Concepts

## Core Principles

### 1. Hexagonal Architecture (Ports & Adapters)

The hexagonal architecture separates business logic from external concerns through well-defined interfaces.

```
                    ┌───────────────────────────────┐
                    │      Infrastructure           │
                    │  ┌─────────────────────────┐  │
                    │  │    Application Layer    │  │
                    │  │  ┌───────────────────┐  │  │
    REST ──────────────▶  │   Domain Layer    │  ◀──────── MongoDB
    gRPC ──────────────▶  │                   │  ◀──────── Pulsar
    Events ────────────▶  │   Pure Business   │  ◀──────── Redis
                    │  │  │      Logic        │  │  │
                    │  │  └───────────────────┘  │  │
                    │  │                         │  │
                    │  └─────────────────────────┘  │
                    └───────────────────────────────┘

    Driving Adapters (Inbound)      Driven Adapters (Outbound)
    - REST Controllers              - Database Repositories
    - gRPC Services                 - Message Publishers
    - Event Listeners               - External API Clients
```

**Key Benefits**:
- Technology independence: Swap databases without changing business logic
- Testability: Test domain in isolation without infrastructure
- Flexibility: Multiple entry points (REST, gRPC, events) use same core logic

### 2. Domain-Driven Design (DDD)

#### Aggregates

An aggregate is a cluster of domain objects treated as a single unit for data changes.

```kotlin
// World is the aggregate root
class WorldAggregate private constructor(
    val id: WorldId,
    val name: String,
    val type: WorldType,
    private val _characters: MutableList<Character> = mutableListOf()
) {
    val characters: List<Character> get() = _characters.toList()

    // All changes go through the aggregate root
    fun addCharacter(character: Character): CharacterAddedEvent {
        require(_characters.size < maxCharacters) { "Max characters reached" }
        _characters.add(character)
        return CharacterAddedEvent(id, character.id)
    }

    companion object {
        fun create(command: CreateWorldCommand): Pair<WorldAggregate, WorldCreatedEvent> {
            val world = WorldAggregate(
                id = WorldId(UUID.randomUUID().toString()),
                name = command.name,
                type = command.type
            )
            return world to WorldCreatedEvent(world.id, world.type)
        }
    }
}
```

**Rules**:
- Only the aggregate root is accessible externally
- Aggregates are loaded and saved as a whole
- Changes produce domain events

#### Value Objects

Immutable objects defined by their attributes, not identity.

```kotlin
@JvmInline
value class WorldId(val value: String) {
    init {
        require(value.isNotBlank()) { "WorldId cannot be blank" }
    }
}

data class Location(
    val latitude: Double,
    val longitude: Double
) {
    init {
        require(latitude in -90.0..90.0) { "Invalid latitude" }
        require(longitude in -180.0..180.0) { "Invalid longitude" }
    }
}
```

#### Domain Events

Events represent something that happened in the domain.

```kotlin
sealed class WorldEvent {
    abstract val worldId: WorldId
    abstract val occurredAt: Instant
}

data class WorldCreatedEvent(
    override val worldId: WorldId,
    val worldType: WorldType,
    override val occurredAt: Instant = Instant.now()
) : WorldEvent()

data class CharacterAddedEvent(
    override val worldId: WorldId,
    val characterId: CharacterId,
    override val occurredAt: Instant = Instant.now()
) : WorldEvent()
```

### 3. Event Sourcing

Instead of storing current state, store the sequence of events that led to the current state.

```kotlin
class WorldAggregate private constructor() {
    lateinit var id: WorldId
    lateinit var name: String
    private val _characters = mutableListOf<Character>()
    private val _pendingEvents = mutableListOf<WorldEvent>()

    val pendingEvents: List<WorldEvent> get() = _pendingEvents.toList()

    // Apply event to change state
    private fun apply(event: WorldEvent) {
        when (event) {
            is WorldCreatedEvent -> {
                this.id = event.worldId
                this.name = event.name
            }
            is CharacterAddedEvent -> {
                _characters.add(Character(event.characterId, event.name))
            }
        }
    }

    // Reconstitute from event stream
    companion object {
        fun fromEvents(events: List<WorldEvent>): WorldAggregate {
            val aggregate = WorldAggregate()
            events.forEach { aggregate.apply(it) }
            return aggregate
        }
    }
}
```

**Benefits**:
- Complete audit trail
- Temporal queries (state at any point in time)
- Event replay for debugging
- Natural fit for CQRS

### 4. CQRS (Command Query Responsibility Segregation)

Separate models for reads and writes.

```
Commands (Write)                    Queries (Read)
     │                                   │
     ▼                                   ▼
┌─────────────┐                   ┌─────────────┐
│  Aggregate  │                   │  Read Model │
│   (Write)   │                   │   (Views)   │
└──────┬──────┘                   └──────▲──────┘
       │                                 │
       │    Events                       │
       └─────────────┬───────────────────┘
                     │
              ┌──────┴──────┐
              │  Projector  │
              └─────────────┘
```

```kotlin
// Write side: Aggregate handles commands
@Transactional
fun createWorld(command: CreateWorldCommand): WorldId {
    val (world, event) = WorldAggregate.create(command)
    eventStore.append(world.id, event)
    return world.id
}

// Read side: Projector builds read model
@EventHandler
fun on(event: WorldCreatedEvent) {
    readModelRepository.save(
        WorldSummary(
            id = event.worldId.value,
            name = event.name,
            characterCount = 0,
            createdAt = event.occurredAt
        )
    )
}
```

### 5. Transactional Outbox Pattern

Ensure reliable event publishing without distributed transactions.

```
┌─────────────────────────────────────────────────────────────┐
│                     Single Transaction                       │
│  ┌─────────────────┐         ┌─────────────────────────┐    │
│  │  Save Aggregate │         │  Insert Outbox Event    │    │
│  │    (MongoDB)    │         │      (same DB)          │    │
│  └─────────────────┘         └─────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
                                         │
                                         │ Async processor
                                         ▼
                               ┌─────────────────────┐
                               │   Message Broker    │
                               │   (Pulsar/Kafka)    │
                               └─────────────────────┘
```

```kotlin
// Repository saves aggregate AND outbox event atomically
@Transactional
fun save(aggregate: WorldAggregate) {
    // Save aggregate state
    mongoTemplate.save(aggregate.toDocument())

    // Save events to outbox (same transaction)
    aggregate.pendingEvents.forEach { event ->
        mongoTemplate.save(
            OutboxEvent(
                id = UUID.randomUUID().toString(),
                aggregateId = aggregate.id.value,
                eventType = event::class.simpleName!!,
                payload = objectMapper.writeValueAsString(event),
                createdAt = Instant.now()
            )
        )
    }
}

// Separate processor publishes to broker
@Scheduled(fixedDelay = 100)
fun processOutbox() {
    val events = outboxRepository.findUnpublished(limit = 100)
    events.forEach { event ->
        try {
            messageBroker.publish(event.payload)
            outboxRepository.markPublished(event.id)
        } catch (e: Exception) {
            // Will retry on next poll
            log.error("Failed to publish event ${event.id}", e)
        }
    }
}
```

### 6. Use Case Pattern

Each business operation is a dedicated use case.

```kotlin
// Port (interface)
interface CreateWorldUseCase {
    fun execute(command: CreateWorldCommand): WorldId
}

// Application service implements the use case
@Service
class CreateWorldUseCaseImpl(
    private val repository: WorldRepository,
    private val eventPublisher: EventPublisher,
    private val validator: WorldValidator
) : CreateWorldUseCase {

    @Transactional
    override fun execute(command: CreateWorldCommand): WorldId {
        // 1. Validate
        validator.validate(command)

        // 2. Execute domain logic
        val (world, event) = WorldAggregate.create(command)

        // 3. Persist
        repository.save(world)

        // 4. Publish event
        eventPublisher.publish(event)

        return world.id
    }
}
```

**Benefits**:
- Single responsibility
- Easy to test in isolation
- Clear entry point for each operation
- Documentation through code

### 7. Repository Pattern

Abstract persistence behind an interface.

```kotlin
// Port (in application layer)
interface WorldRepository {
    fun save(world: WorldAggregate): WorldAggregate
    fun findById(id: WorldId): WorldAggregate?
    fun findByType(type: WorldType): List<WorldAggregate>
}

// Adapter (in infrastructure layer)
@Repository
class MongoWorldRepository(
    private val mongoTemplate: MongoTemplate
) : WorldRepository {

    override fun save(world: WorldAggregate): WorldAggregate {
        mongoTemplate.save(world.toDocument(), "worlds")
        return world
    }

    override fun findById(id: WorldId): WorldAggregate? {
        return mongoTemplate.findById(id.value, WorldDocument::class.java, "worlds")
            ?.toDomain()
    }
}

// In-memory implementation for testing
class InMemoryWorldRepository : WorldRepository {
    private val store = mutableMapOf<WorldId, WorldAggregate>()

    override fun save(world: WorldAggregate) = world.also { store[it.id] = it }
    override fun findById(id: WorldId) = store[id]
}
```

## Architecture Decision Checklist

| Decision | Question | Options |
|----------|----------|---------|
| **State Storage** | Do you need audit trail? | Event Sourcing vs State Storage |
| **Read/Write** | Different read/write patterns? | CQRS vs Single Model |
| **Event Publishing** | Need guaranteed delivery? | Outbox vs Direct Publish |
| **Orchestration** | Long-running workflows? | Workflow Engine vs Event Choreography |
| **External Calls** | Many external dependencies? | Sidecar Pattern vs Direct Integration |
