# Modern Architecture Patterns

Code snippets demonstrating modern software architecture patterns in Kotlin, based on the MMORPG Narrative Engine project.

## Patterns

### Hexagonal Architecture
Clean separation between domain, application, and infrastructure layers.

- **Domain Layer** - Pure business logic, aggregates, events
- **Application Layer** - Use cases (ports), orchestration services
- **Infrastructure Layer** - Adapters for persistence, messaging

```
hexagonal/
├── domain/           # Aggregates, events, value objects
├── application/      # Ports (use cases), application services
└── infrastructure/   # Repository adapters, publishers
```

### Transactional Outbox Pattern
Reliable event publishing with guaranteed delivery.

- Events stored in same transaction as aggregate state
- Separate processor publishes to message broker
- At-least-once delivery without distributed transactions
- Support for polling or MongoDB Change Streams

### Temporal Workflow Orchestration
Durable, fault-tolerant workflow orchestration.

- Document processing pipeline example
- Activity retry configuration
- Saga pattern for compensation

### Workflow-Agnostic Architecture
Treat workflow engines (Temporal, Step Functions) as infrastructure adapters.

- Business logic in domain services, not workflows
- Same orchestrator callable via API, Temporal, Step Functions, or events
- Switch execution modes via Spring profile configuration
- See [docs/workflow-agnostic-architecture.md](docs/workflow-agnostic-architecture.md)

### DAG-based Dimensional Configuration
Hierarchical configuration with inheritance using JGraphT.

- Configuration inheritance through graph traversal
- Override at any level in hierarchy
- Example: GLOBAL -> WORLD_TYPE -> WORLD -> REGION -> LOCATION

### Threshold Configuration
Applied dimensional configuration to narrative engine thresholds.

- AI confidence thresholds per location
- Character limits per scene
- Event trigger probabilities
- Danger levels and content moderation
- REST API adapter for threshold management

### Sidecar Patterns

#### Dapr Building Blocks
Abstract infrastructure behind HTTP APIs - engineers write pure business logic.

- Pub/Sub, State, Service Invocation via HTTP calls
- Swap Kafka for Pulsar without code changes
- No infrastructure SDK dependencies in application code
- See [docs/dapr-building-blocks.md](docs/dapr-building-blocks.md)

#### Envoy Sidecar Authentication
Externalize authentication from application code.

- JWT/PAT validation in Envoy sidecar
- Backend trusts pre-validated headers (X-User-Id, X-User-Roles)
- mTLS for production security
- See [docs/envoy-sidecar-auth.md](docs/envoy-sidecar-auth.md)

## Tech Stack

- **Language:** Kotlin
- **Framework:** Spring Boot 3.x
- **Messaging:** Apache Pulsar
- **Data:** MongoDB
- **Orchestration:** Temporal.io
- **Graph:** JGraphT

## Project Structure

```
modern-patterns/
├── hexagonal/
│   ├── domain/WorldAggregate.kt
│   ├── application/WorldPorts.kt
│   ├── application/WorldApplicationService.kt
│   └── infrastructure/
│       ├── MongoWorldRepository.kt
│       └── ThresholdRestAdapter.kt
├── outbox/
│   └── TransactionalOutbox.kt
├── temporal/
│   └── NarrativeWorkflows.kt
├── dimensional-config/
│   ├── DimensionalConfiguration.kt
│   └── thresholds/ThresholdConfiguration.kt
├── docs/
│   ├── project-structure.md
│   ├── architecture-concepts.md
│   ├── workflow-agnostic-architecture.md
│   ├── dapr-building-blocks.md
│   └── envoy-sidecar-auth.md
└── tests/
    ├── features/threshold_configuration.feature
    ├── cucumber/ThresholdConfigurationSteps.kt
    └── newman/
        ├── threshold-api-collection.json
        ├── local-environment.json
        └── ci-environment.json
```

## Documentation

| Document | Description |
|----------|-------------|
| [Project Structure](docs/project-structure.md) | Recommended folder structure for hexagonal architecture |
| [Architecture Concepts](docs/architecture-concepts.md) | Core patterns: DDD, Event Sourcing, CQRS, Outbox |
| [Workflow-Agnostic Architecture](docs/workflow-agnostic-architecture.md) | Implement workflows as adapters, not core logic |
| [Dapr Building Blocks](docs/dapr-building-blocks.md) | Pure API code with infrastructure abstraction |
| [Envoy Sidecar Auth](docs/envoy-sidecar-auth.md) | Externalize authentication from application code |

## Key Concepts Demonstrated

- **Event Sourcing** - Aggregates rebuilt from event stream
- **CQRS** - Separate read/write models
- **Domain Events** - State changes as immutable events
- **Value Objects** - Immutable domain primitives
- **Use Case Pattern** - Single-responsibility application services
- **Repository Pattern** - Persistence abstraction
- **Saga Pattern** - Distributed transaction compensation
- **BDD Testing** - Gherkin features with Cucumber step definitions
- **API Integration Testing** - Newman/Postman collections

## Testing

### BDD Tests (Cucumber)

```bash
./gradlew cucumber
```

Feature files in `tests/features/` with step definitions in `tests/cucumber/`.

### Integration Tests (Newman)

```bash
# Install Newman
npm install -g newman

# Run tests against local server
newman run tests/newman/threshold-api-collection.json \
  -e tests/newman/local-environment.json
```

See `tests/newman/README.md` for detailed test documentation.

## Author

Eric Diana - Principal Engineer / Architect

## License

MIT
