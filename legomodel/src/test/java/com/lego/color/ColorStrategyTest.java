package com.lego.color;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.lego.model.Brick;
import com.lego.model.ColorRgb;

class ColorStrategyTest {

    @Test
    void directMatchStrategyHasCorrectName() {
        DirectMatchStrategy strategy = new DirectMatchStrategy();
        assertEquals("direct", strategy.name());
        assertFalse(strategy.description().isEmpty());
    }

    @Test
    void directMatchStrategyProducesResults() throws Exception {
        LegoPaletteMapper palette = LegoPaletteMapper.loadDefault();
        DirectMatchStrategy strategy = new DirectMatchStrategy();

        Map<Brick, ColorRgb> input = new HashMap<>();
        Brick brick = new Brick(0, 0, 0, 1, 1, 1);
        input.put(brick, new ColorRgb(1f, 1f, 1f)); // white

        Map<Brick, Integer> result = strategy.apply(input, palette);
        assertEquals(1, result.size());
        assertEquals(15, result.get(brick), "White should map to LDraw code 15");
    }

    @Test
    void directMatchStrategyMatchesLegacyBehavior() throws Exception {
        LegoPaletteMapper palette = LegoPaletteMapper.loadDefault();
        DirectMatchStrategy strategy = new DirectMatchStrategy();

        // Test several colors: the strategy should produce the same result
        // as calling palette.nearestLDrawColor() directly
        ColorRgb[] testColors = {
            new ColorRgb(0f, 0f, 0f),       // black
            new ColorRgb(1f, 1f, 1f),       // white
            new ColorRgb(0.585f, 0.010f, 0.003f), // red
            new ColorRgb(0.5f, 0.2f, 0.1f), // brown-ish
        };

        for (int i = 0; i < testColors.length; i++) {
            Map<Brick, ColorRgb> input = new HashMap<>();
            Brick brick = new Brick(i, 0, 0, 1, 1, 1);
            input.put(brick, testColors[i]);

            Map<Brick, Integer> result = strategy.apply(input, palette);
            int expected = palette.nearestLDrawColor(testColors[i]);
            assertEquals(expected, result.get(brick),
                "Strategy result should match direct palette call for color " + i);
        }
    }

    @Test
    void registryContainsDirectByDefault() {
        ColorStrategyRegistry registry = ColorStrategyRegistry.createDefault();
        assertTrue(registry.availableNames().contains("direct"));
        assertEquals("direct", registry.defaultName());
    }

    @Test
    void registryGetReturnsStrategy() {
        ColorStrategyRegistry registry = ColorStrategyRegistry.createDefault();
        ColorStrategy strategy = registry.get("direct");
        assertNotNull(strategy);
        assertEquals("direct", strategy.name());
    }

    @Test
    void registryGetIsCaseInsensitive() {
        ColorStrategyRegistry registry = ColorStrategyRegistry.createDefault();
        ColorStrategy strategy = registry.get("DIRECT");
        assertEquals("direct", strategy.name());
    }

    @Test
    void registryGetThrowsForUnknown() {
        ColorStrategyRegistry registry = ColorStrategyRegistry.createDefault();
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> registry.get("nonexistent")
        );
        assertTrue(ex.getMessage().contains("nonexistent"));
        assertTrue(ex.getMessage().contains("Available"));
    }

    @Test
    void registryOfCreatesCustomRegistry() {
        ColorStrategy custom = new ColorStrategy() {
            @Override public String name() { return "test-algo"; }
            @Override public String description() { return "A test algorithm"; }
            @Override public Map<Brick, Integer> apply(Map<Brick, ColorRgb> brickColors, LegoPaletteMapper palette) {
                Map<Brick, Integer> result = new HashMap<>();
                brickColors.keySet().forEach(b -> result.put(b, 42));
                return result;
            }
        };

        ColorStrategyRegistry registry = ColorStrategyRegistry.of(custom);
        assertEquals(1, registry.availableNames().size());
        assertTrue(registry.availableNames().contains("test-algo"));
        assertEquals("test-algo", registry.get("test-algo").name());
    }

    @Test
    void registryAllReturnsAllStrategies() {
        ColorStrategyRegistry registry = ColorStrategyRegistry.createDefault();
        Map<String, ColorStrategy> all = registry.all();
        assertFalse(all.isEmpty());
        assertTrue(all.containsKey("direct"));
    }
}
