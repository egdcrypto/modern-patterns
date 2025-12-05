# Workflow-Agnostic Architecture Design

## Problem Statement

Tightly coupling business logic to a specific workflow engine (Temporal, Camunda, Airflow, etc.) creates:
- **Vendor lock-in**: Difficult to switch workflow engines
- **Testing complexity**: Need workflow engine for tests
- **Limited flexibility**: Can't call logic directly via API
- **Migration risk**: Changing engines requires rewriting business logic

## Solution: Hexagonal Architecture with Workflow as Adapter

Treat workflow engines as infrastructure adapters. Business logic lives in domain services.

```
┌─────────────────────────────────────────────────────────────────┐
│                    Execution Adapters                            │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────────────┐ │
│  │ REST API │  │ Temporal │  │ Step Fns │  │ Event Listener   │ │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────────┬─────────┘ │
└───────┼─────────────┼─────────────┼─────────────────┼───────────┘
        │             │             │                 │
        └──────────┬──┴─────────────┴─────────────────┘
                   ▼
        ┌──────────────────────┐
        │ Application Service  │  ← Orchestration Layer
        │  (Orchestrator)      │
        └──────────┬───────────┘
                   │
        ┌──────────┴───────────┐
        │   Domain Services    │  ← Pure Business Logic
        └──────────────────────┘
```

## Implementation

### 1. Domain Service (Pure Business Logic)

```kotlin
// Pure domain service - no workflow dependencies
package patterns.narrative.domain

interface NarrativeService {
    fun createWorld(request: CreateWorldRequest): World
    fun processDocument(documentId: String, content: String): ProcessedDocument
    fun extractEntities(document: ProcessedDocument): List<Entity>
    fun generateNarrative(world: World, entities: List<Entity>): Narrative
}

@Service
class NarrativeServiceImpl(
    private val worldRepository: WorldRepository,
    private val eventPublisher: DomainEventPublisher
) : NarrativeService {

    @Transactional
    override fun createWorld(request: CreateWorldRequest): World {
        val world = World(
            id = UUID.randomUUID().toString(),
            name = request.name,
            type = request.type,
            status = WorldStatus.CREATED,
            createdAt = Instant.now()
        )

        // Save state
        worldRepository.save(world)

        // Publish domain event (using outbox pattern)
        eventPublisher.publish(
            WorldCreatedEvent(
                worldId = world.id,
                worldType = world.type.name
            )
        )

        return world
    }

    @Transactional
    override fun processDocument(documentId: String, content: String): ProcessedDocument {
        // Pure business logic - parse narrative document
        val sections = parseDocumentSections(content)
        val metadata = extractMetadata(content)

        return ProcessedDocument(
            id = documentId,
            sections = sections,
            metadata = metadata,
            processedAt = Instant.now()
        )
    }

    // Other methods follow same pattern - pure business logic
}
```

### 2. Application Service (Orchestration Layer)

```kotlin
// Application service orchestrates domain services
package patterns.narrative.application

interface NarrativeOrchestrator {
    fun executeDocumentProcessingPipeline(request: DocumentRequest): NarrativeResponse
}

@Service
class NarrativeOrchestratorImpl(
    private val narrativeService: NarrativeService,
    private val entityService: EntityExtractionService,
    private val worldService: WorldService,
    private val notificationService: NotificationService
) : NarrativeOrchestrator {

    override fun executeDocumentProcessingPipeline(request: DocumentRequest): NarrativeResponse {
        // Step 1: Process document
        val document = narrativeService.processDocument(
            documentId = request.documentId,
            content = request.content
        )

        // Step 2: Extract entities
        val entities = entityService.extractEntities(document)

        // Step 3: Create or update world
        val world = worldService.getOrCreate(request.worldId)

        // Step 4: Add entities to world
        entities.forEach { entity ->
            worldService.addEntity(world.id, entity)
        }

        // Step 5: Generate narrative
        val narrative = narrativeService.generateNarrative(world, entities)

        // Step 6: Notify completion
        notificationService.notifyCompletion(narrative)

        return NarrativeResponse(
            worldId = world.id,
            narrative = narrative,
            entitiesProcessed = entities.size
        )
    }
}
```

### 3. Multiple Execution Adapters

#### 3a. Direct API Adapter

```kotlin
// Direct REST API - no workflow engine
@RestController
@RequestMapping("/api/narrative")
class NarrativeApiController(
    private val orchestrator: NarrativeOrchestrator
) {

    @PostMapping("/process")
    fun processDocument(@RequestBody request: DocumentRequest): NarrativeResponse {
        // Direct synchronous execution
        return orchestrator.executeDocumentProcessingPipeline(request)
    }

    @PostMapping("/process-async")
    fun processDocumentAsync(@RequestBody request: DocumentRequest): AsyncResponse {
        // Async execution using Spring's @Async or CompletableFuture
        val future = CompletableFuture.supplyAsync {
            orchestrator.executeDocumentProcessingPipeline(request)
        }

        val trackingId = UUID.randomUUID().toString()
        asyncExecutions[trackingId] = future

        return AsyncResponse(
            trackingId = trackingId,
            status = "PROCESSING",
            checkUrl = "/api/narrative/status/$trackingId"
        )
    }
}
```

#### 3b. Temporal Workflow Adapter

```kotlin
// Temporal adapter - wraps the orchestrator
@WorkflowImpl
class DocumentProcessingWorkflowImpl : DocumentProcessingWorkflow {

    // Workflow wraps the orchestrator as an activity
    private val orchestratorActivity = Workflow.newActivityStub(
        NarrativeOrchestratorActivity::class.java,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofMinutes(5))
            .build()
    )

    override fun process(request: DocumentRequest): NarrativeResponse {
        // Delegate to the same orchestrator via activity
        return orchestratorActivity.executeDocumentProcessingPipeline(request)
    }
}

@Component
@ActivityImpl
class NarrativeOrchestratorActivity(
    private val orchestrator: NarrativeOrchestrator
) {
    @ActivityMethod
    fun executeDocumentProcessingPipeline(request: DocumentRequest): NarrativeResponse {
        // Same orchestrator, just wrapped in Temporal activity
        return orchestrator.executeDocumentProcessingPipeline(request)
    }
}
```

#### 3c. AWS Step Functions Adapter

```kotlin
// AWS Step Functions adapter
class NarrativeStepFunctionAdapter(
    private val narrativeService: NarrativeService,
    private val entityService: EntityExtractionService,
    private val worldService: WorldService
) {

    // Each step calls the appropriate service method
    fun processDocumentStep(input: Map<String, Any>): Map<String, Any> {
        val documentId = input["documentId"] as String
        val content = input["content"] as String

        val document = narrativeService.processDocument(documentId, content)

        return mapOf(
            "documentId" to document.id,
            "sections" to document.sections,
            "metadata" to document.metadata
        )
    }

    fun extractEntitiesStep(input: Map<String, Any>): Map<String, Any> {
        val document = parseDocument(input)
        val entities = entityService.extractEntities(document)

        return mapOf(
            "entities" to entities.map { it.toMap() }
        )
    }

    fun createWorldStep(input: Map<String, Any>): Map<String, Any> {
        val worldId = input["worldId"] as String
        val world = worldService.getOrCreate(worldId)

        return mapOf("worldId" to world.id)
    }

    // Additional steps follow same pattern
}
```

#### 3d. Event-Driven Adapter

```kotlin
// Event-driven adapter using message broker
@Component
class DocumentEventListener(
    private val orchestrator: NarrativeOrchestrator
) {

    @EventHandler
    fun handle(event: DocumentUploadedEvent) {
        val request = DocumentRequest(
            documentId = event.documentId,
            worldId = event.worldId,
            content = event.content
        )

        try {
            // Same orchestrator, triggered by events
            val response = orchestrator.executeDocumentProcessingPipeline(request)

            // Publish completion event
            publishCompletionEvent(response)
        } catch (e: Exception) {
            handleError(event, e)
        }
    }
}
```

### 4. Configuration-Based Adapter Selection

```kotlin
// Spring configuration to choose adapter based on profile
@Configuration
class OrchestrationConfig {

    @Bean
    @Profile("temporal")
    fun temporalAdapter(orchestrator: NarrativeOrchestrator): OrchestrationAdapter {
        return TemporalOrchestrationAdapter(orchestrator)
    }

    @Bean
    @Profile("direct")
    fun directAdapter(orchestrator: NarrativeOrchestrator): OrchestrationAdapter {
        return DirectOrchestrationAdapter(orchestrator)
    }

    @Bean
    @Profile("step-functions")
    fun stepFunctionsAdapter(
        narrativeService: NarrativeService,
        entityService: EntityExtractionService,
        worldService: WorldService
    ): OrchestrationAdapter {
        return StepFunctionsOrchestrationAdapter(
            narrativeService, entityService, worldService
        )
    }

    @Bean
    @Profile("event-driven")
    fun eventAdapter(orchestrator: NarrativeOrchestrator): OrchestrationAdapter {
        return EventDrivenOrchestrationAdapter(orchestrator)
    }
}
```

### 5. Testing Without Workflow Engine

```kotlin
@SpringBootTest
class NarrativeOrchestratorTest {

    @MockBean
    private lateinit var narrativeService: NarrativeService

    @MockBean
    private lateinit var entityService: EntityExtractionService

    @Autowired
    private lateinit var orchestrator: NarrativeOrchestrator

    @Test
    fun `should execute document processing without workflow engine`() {
        // Given
        val request = DocumentRequest(
            documentId = "doc-123",
            worldId = "world-456",
            content = "Sample narrative content..."
        )

        val document = ProcessedDocument(id = "doc-123")
        whenever(narrativeService.processDocument(any(), any())).thenReturn(document)
        whenever(entityService.extractEntities(any())).thenReturn(testEntities())

        // When - direct call, no workflow engine needed
        val response = orchestrator.executeDocumentProcessingPipeline(request)

        // Then
        assertThat(response.worldId).isEqualTo("world-456")
        verify(narrativeService).processDocument(any(), any())
        verify(entityService).extractEntities(any())
    }
}
```

## Benefits of This Approach

### 1. Workflow Independence
- Business logic doesn't know about workflow engine
- Can switch from Temporal to Step Functions without changing core logic
- Can run without any workflow engine

### 2. Multiple Execution Modes
```kotlin
// Same business logic, different triggers

// Via REST API
POST /api/narrative/process

// Via Temporal workflow
workflowClient.start(workflow::process, request)

// Via Step Functions
sfnClient.startExecution(stateMachineArn, input)

// Via Event
eventBus.publish(DocumentUploadedEvent(...))

// Via CLI
./narrative-cli process --document-id=123
```

### 3. Easier Testing
```kotlin
// Unit test without workflow engine
@Test
fun testDocumentProcessing() {
    val result = narrativeService.processDocument(id, content)
    assertThat(result.sections).hasSize(5)
}

// Integration test with chosen adapter
@Test
@ActiveProfiles("direct")  // or "temporal", "step-functions"
fun testFullFlow() {
    val response = orchestrationAdapter.execute(request)
    assertThat(response.status).isEqualTo("COMPLETED")
}
```

### 4. Gradual Migration
```kotlin
// Start with direct API
@Profile("phase1")
class DirectExecution

// Move to Temporal for durable workflows
@Profile("phase2")
class TemporalExecution

// Switch to Step Functions in AWS
@Profile("phase3")
class StepFunctionsExecution
```

## Decision Matrix

| Scenario | Recommended Approach |
|----------|---------------------|
| Simple operations | Direct API |
| Complex multi-step flows | Workflow engine via adapter |
| Event-driven processing | Event adapter |
| Need UI/status tracking | Workflow engine or async API |
| Microservices communication | Event-driven or API |
| Batch processing | Workflow engine or job scheduler |
| Real-time requirements | Direct API |
| Need compensation/rollback | Workflow engine via adapter |

## Key Principles

1. **Business logic in domain services** - Never in workflow definitions
2. **Orchestration in application layer** - Coordinates domain services
3. **Adapters for execution modes** - Workflow, API, events are just adapters
4. **Configuration over code changes** - Switch execution modes via config
5. **Test at service level** - Not at workflow level
