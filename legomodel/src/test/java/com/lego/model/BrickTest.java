package com.lego.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class BrickTest {

    @Test
    void testValidBrickCreation() {
        Brick brick = new Brick(0, 0, 0, 1, 1, 1);
        assertEquals(0, brick.x());
        assertEquals(0, brick.y());
        assertEquals(0, brick.z());
        assertEquals(1, brick.studX());
        assertEquals(1, brick.studY());
        assertEquals(1, brick.heightUnits());
    }

    @Test
    void testBrickWith1x2Dimensions() {
        Brick brick = new Brick(5, 10, 3, 1, 2, 1);
        assertEquals(5, brick.x());
        assertEquals(10, brick.y());
        assertEquals(3, brick.z());
        assertEquals(1, brick.studX());
        assertEquals(2, brick.studY());
    }

    @Test
    void testNegativeXThrows() {
        assertThrows(IllegalArgumentException.class, () -> new Brick(-1, 0, 0, 1, 1, 1));
    }

    @Test
    void testNegativeYThrows() {
        assertThrows(IllegalArgumentException.class, () -> new Brick(0, -1, 0, 1, 1, 1));
    }

    @Test
    void testNegativeZThrows() {
        assertThrows(IllegalArgumentException.class, () -> new Brick(0, 0, -1, 1, 1, 1));
    }

    @Test
    void testZeroStudXThrows() {
        assertThrows(IllegalArgumentException.class, () -> new Brick(0, 0, 0, 0, 1, 1));
    }

    @Test
    void testNegativeStudXThrows() {
        assertThrows(IllegalArgumentException.class, () -> new Brick(0, 0, 0, -1, 1, 1));
    }

    @Test
    void testZeroStudYThrows() {
        assertThrows(IllegalArgumentException.class, () -> new Brick(0, 0, 0, 1, 0, 1));
    }

    @Test
    void testNegativeStudYThrows() {
        assertThrows(IllegalArgumentException.class, () -> new Brick(0, 0, 0, 1, -1, 1));
    }

    @Test
    void testZeroHeightUnitsThrows() {
        assertThrows(IllegalArgumentException.class, () -> new Brick(0, 0, 0, 1, 1, 0));
    }

    @Test
    void testNegativeHeightUnitsThrows() {
        assertThrows(IllegalArgumentException.class, () -> new Brick(0, 0, 0, 1, 1, -1));
    }

    @Test
    void testMaxX() {
        Brick brick = new Brick(5, 0, 0, 3, 1, 1);
        assertEquals(8, brick.maxX());
    }

    @Test
    void testMaxY() {
        Brick brick = new Brick(0, 5, 0, 1, 3, 1);
        assertEquals(8, brick.maxY());
    }

    @Test
    void testMaxZ() {
        Brick brick = new Brick(0, 0, 5, 1, 1, 3);
        assertEquals(8, brick.maxZ());
    }

    @Test
    void testNoOverlap_Separated() {
        Brick brick1 = new Brick(0, 0, 0, 1, 1, 1);
        Brick brick2 = new Brick(2, 0, 0, 1, 1, 1);
        assertFalse(brick1.overlaps(brick2));
        assertFalse(brick2.overlaps(brick1));
    }

    @Test
    void testNoOverlap_Adjacent() {
        Brick brick1 = new Brick(0, 0, 0, 2, 1, 1);
        Brick brick2 = new Brick(2, 0, 0, 1, 1, 1);
        assertFalse(brick1.overlaps(brick2));
        assertFalse(brick2.overlaps(brick1));
    }

    @Test
    void testOverlap_Identical() {
        Brick brick1 = new Brick(0, 0, 0, 1, 1, 1);
        Brick brick2 = new Brick(0, 0, 0, 1, 1, 1);
        assertTrue(brick1.overlaps(brick2));
    }

    @Test
    void testOverlap_Partial() {
        Brick brick1 = new Brick(0, 0, 0, 2, 2, 1);
        Brick brick2 = new Brick(1, 1, 0, 2, 2, 1);
        assertTrue(brick1.overlaps(brick2));
        assertTrue(brick2.overlaps(brick1));
    }

    @Test
    void testNoOverlap_DifferentZLevels() {
        Brick brick1 = new Brick(0, 0, 0, 1, 1, 1);
        Brick brick2 = new Brick(0, 0, 1, 1, 1, 1);
        assertFalse(brick1.overlaps(brick2));
        assertFalse(brick2.overlaps(brick1));
    }

    @Test
    void testOverlap_SpanningMultipleLayers() {
        Brick brick1 = new Brick(0, 0, 0, 1, 1, 3);
        Brick brick2 = new Brick(0, 0, 2, 1, 1, 2);
        assertTrue(brick1.overlaps(brick2));
        assertTrue(brick2.overlaps(brick1));
    }
}
