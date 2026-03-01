package com.lego.data;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.lego.model.CatalogPart;

/**
 * Integration test for CuratedCatalogLoader using the actual catalog file.
 */
class CuratedCatalogLoaderIntegrationTest {

    @Test
    void testLoadActiveParts_ActualFile() {
        // Act: Load active parts from actual catalog
        List<CatalogPart> parts = CuratedCatalogLoader.loadActiveParts();

        // Assert: Should have active parts
        assertFalse(parts.isEmpty(), "Catalog should contain active parts");
        
        // All loaded parts should be active
        assertTrue(parts.stream().allMatch(CatalogPart::active),
            "All loaded parts should have active=true");
        
        // Verify expected active parts are present
        assertTrue(parts.stream().anyMatch(p -> p.partId().equals("3001")),
            "Should contain part 3001 (Brick 2x4)");
        assertTrue(parts.stream().anyMatch(p -> p.partId().equals("3005")),
            "Should contain part 3005 (Brick 1x1)");
        
        // Verify a known active part's details
        CatalogPart brick1x1 = parts.stream()
            .filter(p -> p.partId().equals("3005"))
            .findFirst()
            .orElseThrow();
        
        assertEquals("Brick 1 x 1", brick1x1.name());
        assertEquals(11, brick1x1.categoryId());
        assertEquals("Bricks", brick1x1.categoryName());
        assertEquals(1, brick1x1.studX());
        assertEquals(1, brick1x1.studY());
        assertEquals("1", brick1x1.heightUnitsRaw());
        assertEquals("Plastic", brick1x1.material());
        assertTrue(brick1x1.active());
    }

    @Test
    void testLoadAllParts_ActualFile() {
        // Act: Load all parts from actual catalog
        List<CatalogPart> allParts = CuratedCatalogLoader.loadAllParts();
        List<CatalogPart> activeParts = CuratedCatalogLoader.loadActiveParts();

        // Assert: Should have more total parts than active parts
        assertTrue(allParts.size() >= activeParts.size(),
            "Total parts should be >= active parts");
        
        // Should contain both active and inactive parts
        assertTrue(allParts.stream().anyMatch(CatalogPart::active),
            "Should contain active parts");
        assertTrue(allParts.stream().anyMatch(p -> !p.active()),
            "Should contain inactive parts");
        
        // Verify a known inactive part exists
        assertTrue(allParts.stream().anyMatch(p -> p.partId().equals("3002")),
            "Should contain part 3002 (inactive Brick 2x3)");
    }

    @Test
    void testLoadActiveParts_VerifyStudDimensions() {
        // Act: Load active parts
        List<CatalogPart> parts = CuratedCatalogLoader.loadActiveParts();

        // Assert: All parts should have valid stud dimensions
        assertTrue(parts.stream().allMatch(p -> p.studX() > 0),
            "All parts should have studX > 0");
        assertTrue(parts.stream().allMatch(p -> p.studY() > 0),
            "All parts should have studY > 0");
    }

    @Test
    void testLoadActiveParts_VerifyHeightFormats() {
        // Act: Load active parts
        List<CatalogPart> parts = CuratedCatalogLoader.loadActiveParts();

        // Assert: Should have parts with different height formats
        assertTrue(parts.stream().anyMatch(p -> p.heightUnitsRaw().equals("1")),
            "Should have parts with height '1'");
        assertTrue(parts.stream().anyMatch(p -> p.heightUnitsRaw().equals("1/3")),
            "Should have parts with height '1/3'");
    }
}
