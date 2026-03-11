package com.lego.color;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Registry of available {@link ColorStrategy} implementations.
 *
 * <p>Maps short CLI names (e.g. "direct") to strategy instances. The registry
 * is used by the CLI to resolve {@code --color-algorithm=<name>} flags and to
 * list available algorithms with {@code --color-algorithm=list}.
 *
 * <p>To register a new algorithm, add it in {@link #createDefault()}.
 */
public final class ColorStrategyRegistry {

    private final Map<String, ColorStrategy> strategies;

    private ColorStrategyRegistry(Map<String, ColorStrategy> strategies) {
        this.strategies = Collections.unmodifiableMap(new LinkedHashMap<>(strategies));
    }

    /**
     * Creates the default registry with all built-in strategies.
     * New algorithms should be registered here.
     */
    public static ColorStrategyRegistry createDefault() {
        Map<String, ColorStrategy> map = new LinkedHashMap<>();
        register(map, new DirectMatchStrategy());
        register(map, new UVLabPaletteProjection());
        register(map, new DominantVoteStrategy());
        register(map, new SupersampledVoxelColorPipeline());
        return new ColorStrategyRegistry(map);
    }

    private static void register(Map<String, ColorStrategy> map, ColorStrategy strategy) {
        map.put(strategy.name(), strategy);
    }

    /**
     * Creates the registry with explicit strategy list (for testing).
     */
    public static ColorStrategyRegistry of(ColorStrategy... strategies) {
        Map<String, ColorStrategy> map = new LinkedHashMap<>();
        for (ColorStrategy s : strategies) {
            map.put(s.name(), s);
        }
        return new ColorStrategyRegistry(map);
    }

    /**
     * Looks up a strategy by its CLI name.
     *
     * @param name the strategy name (case-insensitive)
     * @return the strategy
     * @throws IllegalArgumentException if no strategy with that name exists
     */
    public ColorStrategy get(String name) {
        ColorStrategy strategy = strategies.get(name.toLowerCase());
        if (strategy == null) {
            throw new IllegalArgumentException(
                "Unknown color algorithm: '" + name + "'. Available: " + availableNames()
            );
        }
        return strategy;
    }

    /**
     * Returns the set of registered strategy names, in registration order.
     */
    public Set<String> availableNames() {
        return strategies.keySet();
    }

    /**
     * Returns all registered strategies, in registration order.
     */
    public Map<String, ColorStrategy> all() {
        return strategies;
    }

    /**
     * Returns the default strategy name.
     */
    public String defaultName() {
        return "direct";
    }
}
