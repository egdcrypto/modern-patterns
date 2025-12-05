package patterns.dimensional

import org.jgrapht.graph.DirectedAcyclicGraph
import org.jgrapht.graph.DefaultEdge

/**
 * Dimensional Configuration using Directed Acyclic Graph (DAG).
 *
 * Enables hierarchical configuration where settings can be inherited
 * and overridden at different dimensional levels.
 *
 * Example hierarchy for narrative worlds:
 * GLOBAL -> WORLD_TYPE -> WORLD -> REGION -> LOCATION
 *
 * Uses JGraphT for graph operations ensuring no circular dependencies.
 */

// ============================================
// Dimension Types
// ============================================

data class Dimension(
    val type: DimensionType,
    val value: String
) {
    override fun toString(): String = "${type.name}:$value"
}

enum class DimensionType {
    GLOBAL,       // Default settings for all worlds
    WORLD_TYPE,   // Fantasy, Sci-Fi, Historical, etc.
    WORLD,        // Specific world instance
    REGION,       // Region within a world
    LOCATION      // Specific location - most granular
}

// ============================================
// Configuration Value
// ============================================

data class DimensionalValue<T>(
    val dimension: Dimension,
    val key: String,
    val value: T,
    val priority: Int = 0
)

// ============================================
// DAG-based Configuration Graph
// ============================================

/**
 * Graph-based configuration resolver.
 * Traverses the DAG to find the most specific configuration value.
 */
class DimensionalConfigurationGraph<T> {

    private val graph = DirectedAcyclicGraph<Dimension, DefaultEdge>(DefaultEdge::class.java)
    private val configurations = mutableMapOf<Dimension, MutableMap<String, DimensionalValue<T>>>()

    /**
     * Add a dimension node to the graph.
     */
    fun addDimension(dimension: Dimension) {
        graph.addVertex(dimension)
    }

    /**
     * Establish parent-child hierarchy.
     * Child inherits from parent unless overridden.
     */
    fun addHierarchy(parent: Dimension, child: Dimension) {
        if (!graph.containsVertex(parent)) graph.addVertex(parent)
        if (!graph.containsVertex(child)) graph.addVertex(child)
        graph.addEdge(parent, child)
    }

    /**
     * Set configuration at a specific dimension.
     */
    fun setConfiguration(dimension: Dimension, key: String, value: T, priority: Int = 0) {
        configurations
            .getOrPut(dimension) { mutableMapOf() }[key] = DimensionalValue(dimension, key, value, priority)
    }

    /**
     * Resolve configuration by traversing ancestors.
     * Returns the most specific (closest ancestor) value found.
     */
    fun resolve(dimension: Dimension, key: String): T? {
        // Check current dimension first
        configurations[dimension]?.get(key)?.let { return it.value }

        // Traverse ancestors looking for configuration
        val ancestors = getAncestors(dimension)
        for (ancestor in ancestors.sortedByDescending { it.type.ordinal }) {
            configurations[ancestor]?.get(key)?.let { return it.value }
        }

        return null
    }

    /**
     * Get effective configuration including all inherited values.
     */
    fun getEffectiveConfiguration(dimension: Dimension): Map<String, T> {
        val result = mutableMapOf<String, T>()

        // Collect from ancestors first (will be overwritten by more specific)
        val ancestors = getAncestors(dimension).sortedBy { it.type.ordinal }
        for (ancestor in ancestors) {
            configurations[ancestor]?.forEach { (key, value) ->
                result[key] = value.value
            }
        }

        // Apply current dimension's configurations (most specific)
        configurations[dimension]?.forEach { (key, value) ->
            result[key] = value.value
        }

        return result
    }

    private fun getAncestors(dimension: Dimension): Set<Dimension> {
        val ancestors = mutableSetOf<Dimension>()
        val visited = mutableSetOf<Dimension>()
        collectAncestors(dimension, ancestors, visited)
        return ancestors
    }

    private fun collectAncestors(
        dimension: Dimension,
        ancestors: MutableSet<Dimension>,
        visited: MutableSet<Dimension>
    ) {
        if (dimension in visited) return
        visited.add(dimension)

        graph.incomingEdgesOf(dimension).forEach { edge ->
            val parent = graph.getEdgeSource(edge)
            ancestors.add(parent)
            collectAncestors(parent, ancestors, visited)
        }
    }
}

// ============================================
// Example: Narrative World Configuration
// ============================================

/**
 * Example demonstrating hierarchical world configuration.
 */
fun narrativeWorldExample() {
    val config = DimensionalConfigurationGraph<Any>()

    // Build dimension hierarchy
    val global = Dimension(DimensionType.GLOBAL, "default")
    val fantasy = Dimension(DimensionType.WORLD_TYPE, "FANTASY")
    val middleEarth = Dimension(DimensionType.WORLD, "middle-earth")
    val shire = Dimension(DimensionType.REGION, "shire")
    val bagEnd = Dimension(DimensionType.LOCATION, "bag-end")

    config.addHierarchy(global, fantasy)
    config.addHierarchy(fantasy, middleEarth)
    config.addHierarchy(middleEarth, shire)
    config.addHierarchy(shire, bagEnd)

    // Set configurations at different levels
    config.setConfiguration(global, "maxCharactersPerLocation", 100)
    config.setConfiguration(global, "defaultLanguage", "Common")
    config.setConfiguration(fantasy, "magicEnabled", true)
    config.setConfiguration(fantasy, "defaultLanguage", "Elvish")
    config.setConfiguration(middleEarth, "timeScale", 1.0)
    config.setConfiguration(shire, "dangerLevel", "LOW")
    config.setConfiguration(bagEnd, "ownerCharacterId", "bilbo-001")

    // Resolve configuration for Bag End
    val magicEnabled = config.resolve(bagEnd, "magicEnabled")      // true (from FANTASY)
    val language = config.resolve(bagEnd, "defaultLanguage")       // "Elvish" (from FANTASY)
    val dangerLevel = config.resolve(bagEnd, "dangerLevel")        // "LOW" (from SHIRE)
    val owner = config.resolve(bagEnd, "ownerCharacterId")         // "bilbo-001" (from BAG_END)

    // Get all effective config for Bag End
    val effectiveConfig = config.getEffectiveConfiguration(bagEnd)
    // Contains: maxCharactersPerLocation, defaultLanguage, magicEnabled, timeScale, dangerLevel, ownerCharacterId
}
