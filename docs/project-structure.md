# Project Structure Guide

## Overview

This document describes the recommended folder structure for a Kotlin/Spring Boot service following hexagonal architecture principles. The structure supports clean separation of concerns, testability, and maintainability.

## Folder Structure

```
service-name/
├── src/main/kotlin/com/company/service/
│   ├── domain/                    # Pure business logic
│   │   ├── model/                 # Domain entities and value objects
│   │   │   ├── World.kt
│   │   │   ├── Character.kt
│   │   │   └── Location.kt
│   │   ├── event/                 # Domain events
│   │   │   ├── WorldCreatedEvent.kt
│   │   │   └── CharacterAddedEvent.kt
│   │   ├── aggregate/             # Aggregate roots (event-sourced)
│   │   │   └── WorldAggregate.kt
│   │   └── service/               # Domain services (pure logic)
│   │       └── NarrativeRules.kt
│   │
│   ├── application/               # Use cases and orchestration
│   │   ├── port/                  # Ports (interfaces)
│   │   │   ├── inbound/           # Driving ports (use cases)
│   │   │   │   ├── CreateWorldUseCase.kt
│   │   │   │   └── QueryWorldsUseCase.kt
│   │   │   └── outbound/          # Driven ports (dependencies)
│   │   │       ├── WorldRepository.kt
│   │   │       └── EventPublisher.kt
│   │   ├── command/               # Command DTOs
│   │   │   └── CreateWorldCommand.kt
│   │   └── service/               # Application services
│   │       └── WorldApplicationService.kt
│   │
│   ├── infrastructure/            # External adapters
│   │   ├── adapter/
│   │   │   ├── inbound/           # Driving adapters
│   │   │   │   ├── rest/          # REST controllers
│   │   │   │   │   └── WorldController.kt
│   │   │   │   ├── grpc/          # gRPC services
│   │   │   │   └── event/         # Event listeners
│   │   │   │       └── CloudEventListener.kt
│   │   │   └── outbound/          # Driven adapters
│   │   │       ├── persistence/   # Database implementations
│   │   │       │   └── MongoWorldRepository.kt
│   │   │       ├── messaging/     # Message broker implementations
│   │   │       │   └── PulsarEventPublisher.kt
│   │   │       └── external/      # External API clients
│   │   │           └── EnrichmentClient.kt
│   │   ├── config/                # Spring configuration
│   │   │   ├── MongoConfig.kt
│   │   │   └── SecurityConfig.kt
│   │   └── outbox/                # Transactional outbox
│   │       └── OutboxProcessor.kt
│   │
│   └── Application.kt             # Spring Boot entry point
│
├── src/test/kotlin/               # Test sources (mirrors main structure)
│   ├── domain/                    # Unit tests for domain
│   ├── application/               # Unit tests for application layer
│   └── integration/               # Integration tests
│
├── src/test/resources/
│   ├── features/                  # Gherkin feature files
│   └── application-test.yml
│
└── build.gradle.kts
```

## Layer Responsibilities

### Domain Layer (`domain/`)

**Purpose**: Contains pure business logic with no external dependencies.

```kotlin
// domain/model/World.kt
data class World(
    val id: WorldId,
    val name: String,
    val type: WorldType,
    val characters: List<Character> = emptyList(),
    val createdAt: Instant
)

// Value object
@JvmInline
value class WorldId(val value: String)
```

**Rules**:
- No Spring annotations
- No infrastructure dependencies
- Pure Kotlin data classes and business logic
- Can depend on: nothing external

### Application Layer (`application/`)

**Purpose**: Orchestrates domain logic and defines the application's use cases.

```kotlin
// application/port/inbound/CreateWorldUseCase.kt
interface CreateWorldUseCase {
    fun execute(command: CreateWorldCommand): WorldId
}

// application/port/outbound/WorldRepository.kt
interface WorldRepository {
    fun save(world: World): World
    fun findById(id: WorldId): World?
}

// application/service/WorldApplicationService.kt
@Service
class WorldApplicationService(
    private val repository: WorldRepository,
    private val eventPublisher: EventPublisher
) : CreateWorldUseCase {

    @Transactional
    override fun execute(command: CreateWorldCommand): WorldId {
        val world = World.create(command)
        repository.save(world)
        eventPublisher.publish(WorldCreatedEvent(world))
        return world.id
    }
}
```

**Rules**:
- Depends on domain layer only
- Defines ports (interfaces) for external dependencies
- Contains no infrastructure code
- Orchestrates but doesn't contain business rules

### Infrastructure Layer (`infrastructure/`)

**Purpose**: Implements adapters for external systems.

```kotlin
// infrastructure/adapter/outbound/persistence/MongoWorldRepository.kt
@Repository
class MongoWorldRepository(
    private val mongoTemplate: MongoTemplate
) : WorldRepository {

    override fun save(world: World): World {
        mongoTemplate.save(world.toDocument())
        return world
    }

    override fun findById(id: WorldId): World? {
        return mongoTemplate.findById(id.value, WorldDocument::class.java)
            ?.toDomain()
    }
}

// infrastructure/adapter/inbound/rest/WorldController.kt
@RestController
@RequestMapping("/api/v1/worlds")
class WorldController(
    private val createWorldUseCase: CreateWorldUseCase
) {
    @PostMapping
    fun createWorld(@RequestBody request: CreateWorldRequest): ResponseEntity<WorldResponse> {
        val worldId = createWorldUseCase.execute(request.toCommand())
        return ResponseEntity.created(URI("/api/v1/worlds/${worldId.value}")).build()
    }
}
```

**Rules**:
- Implements port interfaces
- Contains all Spring annotations
- Handles serialization/deserialization
- Manages transactions at adapter boundaries

## Dependency Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                    Infrastructure Layer                          │
│  ┌─────────────┐                           ┌─────────────────┐  │
│  │ REST        │                           │ MongoDB         │  │
│  │ Controller  │                           │ Repository      │  │
│  └──────┬──────┘                           └────────▲────────┘  │
│         │                                           │           │
└─────────┼───────────────────────────────────────────┼───────────┘
          │ calls                                     │ implements
          ▼                                           │
┌─────────────────────────────────────────────────────────────────┐
│                    Application Layer                             │
│         ┌────────────────────────────────────────┐              │
│         │       Application Service              │              │
│         │  (implements CreateWorldUseCase)       │              │
│         │  (depends on WorldRepository port)     │              │
│         └──────────────────┬─────────────────────┘              │
│                            │                                     │
└────────────────────────────┼─────────────────────────────────────┘
                             │ uses
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Domain Layer                                │
│              ┌─────────────────────────┐                        │
│              │   World (Aggregate)     │                        │
│              │   Character (Entity)    │                        │
│              │   WorldId (Value Obj)   │                        │
│              └─────────────────────────┘                        │
└─────────────────────────────────────────────────────────────────┘
```

## Module Organization for Multi-Domain Services

For services with multiple bounded contexts:

```
service-name/
├── domains/
│   ├── world/                     # World bounded context
│   │   ├── domain/
│   │   ├── application/
│   │   └── infrastructure/
│   │
│   └── character/                 # Character bounded context
│       ├── domain/
│       ├── application/
│       └── infrastructure/
│
├── shared/                        # Shared kernel
│   ├── event/                     # Shared event types
│   ├── types/                     # Common value objects
│   └── util/                      # Utilities
│
└── infrastructure/                # Cross-cutting infrastructure
    ├── config/
    └── security/
```

## Testing Structure

```
src/test/kotlin/
├── domain/                        # Unit tests (fast, no Spring)
│   └── WorldAggregateTest.kt
│
├── application/                   # Unit tests with mocks
│   └── WorldApplicationServiceTest.kt
│
├── integration/                   # Full integration tests
│   ├── WorldControllerIT.kt
│   └── WorldRepositoryIT.kt
│
└── acceptance/                    # BDD acceptance tests
    ├── steps/
    │   └── WorldSteps.kt
    └── CucumberRunner.kt

src/test/resources/
└── features/
    └── world_creation.feature
```

## Key Principles

### 1. Dependency Rule
Dependencies point inward. Domain has no dependencies, application depends on domain, infrastructure depends on both.

### 2. Port/Adapter Pattern
- **Ports**: Interfaces defined in application layer
- **Adapters**: Implementations in infrastructure layer
- Allows swapping implementations without changing business logic

### 3. No Framework in Domain
The domain layer should be pure Kotlin with no Spring, no JPA, no external framework annotations.

### 4. Single Responsibility
Each class has one reason to change. Controllers handle HTTP, services handle orchestration, repositories handle persistence.

### 5. Testability
- Domain: Unit tests with no mocking needed
- Application: Unit tests with mocked ports
- Infrastructure: Integration tests with real dependencies
