# Dapr Building Blocks

## Overview

Dapr (Distributed Application Runtime) provides building blocks that abstract common distributed systems patterns. Engineers write pure API code while Dapr handles the infrastructure concerns through sidecars.

## The Sidecar Pattern

```
┌─────────────────────────────────────────────────────────────────┐
│                          Kubernetes Pod                          │
│                                                                  │
│  ┌─────────────────────────┐    ┌────────────────────────────┐  │
│  │                         │    │                            │  │
│  │    Application          │◀──▶│    Dapr Sidecar            │  │
│  │    Container            │    │    (daprd)                 │  │
│  │                         │    │                            │  │
│  │  - Pure business logic  │    │  - Service invocation      │  │
│  │  - HTTP/gRPC calls      │    │  - Pub/Sub                 │  │
│  │  - No infrastructure    │    │  - State management        │  │
│  │    dependencies         │    │  - Secrets                 │  │
│  │                         │    │  - Observability           │  │
│  └─────────────────────────┘    └────────────────────────────┘  │
│            │                                 │                   │
│            │  localhost:3500                 │                   │
│            └─────────────────────────────────┘                   │
└─────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
              ┌───────────────────────────────────────┐
              │        External Infrastructure        │
              │                                       │
              │   Kafka    MongoDB    Redis    Vault  │
              └───────────────────────────────────────┘
```

## Key Benefit: Pure API Code

Without Dapr, your code is coupled to specific infrastructure:

```kotlin
// ❌ BEFORE: Code coupled to Kafka
@Service
class OrderService(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val kafkaProperties: KafkaProperties
) {
    fun publishOrder(order: Order) {
        kafkaTemplate.send(
            "orders",
            order.id,
            objectMapper.writeValueAsString(order)
        )
    }
}
```

With Dapr, your code calls a standard HTTP API:

```kotlin
// ✅ AFTER: Pure HTTP call, Dapr handles the rest
@Service
class OrderService(
    private val restTemplate: RestTemplate
) {
    fun publishOrder(order: Order) {
        // Just HTTP POST to Dapr sidecar
        restTemplate.postForEntity(
            "http://localhost:3500/v1.0/publish/pubsub/orders",
            order,
            Void::class.java
        )
    }
}
```

## Building Blocks

### 1. Pub/Sub Messaging

**Application Code** (pure HTTP):

```kotlin
@RestController
class EventPublisher(private val restTemplate: RestTemplate) {

    // Publish event via Dapr
    @PostMapping("/worlds")
    fun createWorld(@RequestBody request: CreateWorldRequest): WorldId {
        val world = worldService.create(request)

        // Publish to Dapr - it routes to configured broker
        restTemplate.postForEntity(
            "http://localhost:3500/v1.0/publish/pubsub/world-events",
            WorldCreatedEvent(world.id, world.name),
            Void::class.java
        )

        return world.id
    }
}

// Subscribe to events - just annotate your endpoint
@RestController
class EventSubscriber {

    @PostMapping("/events/world-created")
    fun handleWorldCreated(@RequestBody event: CloudEvent<WorldCreatedEvent>) {
        // Process event
        projectionService.project(event.data)
    }
}
```

**Dapr Configuration** (YAML):

```yaml
# pubsub.yaml - Swap Kafka for Pulsar without code changes
apiVersion: dapr.io/v1alpha1
kind: Component
metadata:
  name: pubsub
spec:
  type: pubsub.kafka  # or pubsub.pulsar, pubsub.rabbitmq, etc.
  version: v1
  metadata:
  - name: brokers
    value: "kafka:9092"
  - name: consumerGroup
    value: "narrative-engine"
```

### 2. State Management

**Application Code**:

```kotlin
@RestController
class SessionController(private val restTemplate: RestTemplate) {

    // Save state
    @PostMapping("/sessions/{sessionId}")
    fun saveSession(@PathVariable sessionId: String, @RequestBody session: Session) {
        restTemplate.postForEntity(
            "http://localhost:3500/v1.0/state/statestore",
            listOf(StateItem(sessionId, session)),
            Void::class.java
        )
    }

    // Get state
    @GetMapping("/sessions/{sessionId}")
    fun getSession(@PathVariable sessionId: String): Session? {
        return restTemplate.getForObject(
            "http://localhost:3500/v1.0/state/statestore/$sessionId",
            Session::class.java
        )
    }
}

data class StateItem<T>(val key: String, val value: T)
```

**Dapr Configuration**:

```yaml
# statestore.yaml - Swap Redis for MongoDB without code changes
apiVersion: dapr.io/v1alpha1
kind: Component
metadata:
  name: statestore
spec:
  type: state.redis  # or state.mongodb, state.postgresql
  version: v1
  metadata:
  - name: redisHost
    value: "redis:6379"
```

### 3. Service Invocation

**Application Code**:

```kotlin
@Service
class EnrichmentService(private val restTemplate: RestTemplate) {

    // Call another service via Dapr
    fun getCharacterDetails(characterId: String): CharacterDetails {
        return restTemplate.getForObject(
            "http://localhost:3500/v1.0/invoke/character-service/method/characters/$characterId",
            CharacterDetails::class.java
        )!!
    }
}
```

Dapr handles:
- Service discovery
- Load balancing
- Retries
- mTLS encryption
- Distributed tracing

### 4. Secrets Management

**Application Code**:

```kotlin
@Service
class SecretService(private val restTemplate: RestTemplate) {

    fun getApiKey(): String {
        val response = restTemplate.getForObject(
            "http://localhost:3500/v1.0/secrets/vault/api-key",
            Map::class.java
        ) as Map<String, String>
        return response["api-key"]!!
    }
}
```

**Dapr Configuration**:

```yaml
# secrets.yaml
apiVersion: dapr.io/v1alpha1
kind: Component
metadata:
  name: vault
spec:
  type: secretstores.hashicorp.vault
  version: v1
  metadata:
  - name: vaultAddr
    value: "http://vault:8200"
  - name: vaultToken
    secretKeyRef:
      name: vault-token
      key: token
```

## Spring Boot Integration

### Dapr SDK (Optional)

While you can use pure HTTP, the Dapr SDK provides type-safe clients:

```kotlin
// Using Dapr Spring Boot starter
@Service
class WorldService(
    private val daprClient: DaprClient
) {
    suspend fun publishEvent(event: WorldCreatedEvent) {
        daprClient.publishEvent("pubsub", "world-events", event).await()
    }

    suspend fun saveState(key: String, value: Any) {
        daprClient.saveState("statestore", key, value).await()
    }

    suspend fun invokeService(appId: String, method: String): String {
        return daprClient.invokeMethod(appId, method, null, String::class.java).await()
    }
}
```

### Configuration

```yaml
# application.yml
dapr:
  http:
    port: 3500
  grpc:
    port: 50001

spring:
  profiles:
    active: dapr  # Enable Dapr integration
```

## Kubernetes Deployment

### Sidecar Injection

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: narrative-engine
spec:
  template:
    metadata:
      annotations:
        # Dapr sidecar injection
        dapr.io/enabled: "true"
        dapr.io/app-id: "narrative-engine"
        dapr.io/app-port: "8080"
        dapr.io/enable-profiling: "true"
    spec:
      containers:
      - name: app
        image: narrative-engine:latest
        ports:
        - containerPort: 8080
        # App only knows about HTTP - no Kafka, Redis, etc.
        env:
        - name: DAPR_HTTP_PORT
          value: "3500"
```

## Benefits Summary

| Without Dapr | With Dapr |
|--------------|-----------|
| Kafka client dependency | HTTP POST to sidecar |
| Redis client dependency | HTTP GET/POST to sidecar |
| Service mesh configuration | Automatic service invocation |
| Secret management code | HTTP call to sidecar |
| Custom retry logic | Configured in component |
| Multiple broker SDKs | Single HTTP interface |

## When to Use Dapr

**Good Fit**:
- Microservices with diverse infrastructure needs
- Multi-cloud deployments
- Polyglot environments (Kotlin, Go, Python)
- Teams wanting infrastructure abstraction

**Consider Alternatives**:
- Simple applications with one or two dependencies
- Extremely low-latency requirements (sidecar adds ~1ms)
- Existing investment in specific SDKs

## Migration Strategy

1. **Start with one building block**: Begin with Pub/Sub or State
2. **Keep existing code**: Dapr coexists with direct integrations
3. **Migrate incrementally**: Move one component at a time
4. **Use feature flags**: Switch between direct and Dapr calls
