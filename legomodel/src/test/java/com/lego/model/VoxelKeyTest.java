package com.lego.model;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class VoxelKeyTest {

    @Test
    void packUnpackRoundTrip() {
        assertRoundTrip(0, 0, 0);
        assertRoundTrip(1, 2, 3);
        assertRoundTrip(100, 200, 300);
        assertRoundTrip(1000, 500, 750);
    }

    @Test
    void packUnpackMaxValues() {
        // 21-bit max = 2097151
        int max = (1 << 21) - 1;
        assertRoundTrip(max, max, max);
        assertRoundTrip(max, 0, 0);
        assertRoundTrip(0, max, 0);
        assertRoundTrip(0, 0, max);
    }

    @Test
    void differentCoordinatesProduceDifferentKeys() {
        long k1 = VoxelKey.pack(1, 2, 3);
        long k2 = VoxelKey.pack(3, 2, 1);
        long k3 = VoxelKey.pack(1, 3, 2);
        assertNotEquals(k1, k2);
        assertNotEquals(k1, k3);
        assertNotEquals(k2, k3);
    }

    @Test
    void sameCoordinatesProduceSameKey() {
        assertEquals(VoxelKey.pack(10, 20, 30), VoxelKey.pack(10, 20, 30));
    }

    @Test
    void zeroKeyUnpacksToZeros() {
        long key = VoxelKey.pack(0, 0, 0);
        assertEquals(0, VoxelKey.unpackX(key));
        assertEquals(0, VoxelKey.unpackY(key));
        assertEquals(0, VoxelKey.unpackZ(key));
    }

    private static void assertRoundTrip(int x, int y, int z) {
        long key = VoxelKey.pack(x, y, z);
        assertEquals(x, VoxelKey.unpackX(key), "X mismatch for (" + x + "," + y + "," + z + ")");
        assertEquals(y, VoxelKey.unpackY(key), "Y mismatch for (" + x + "," + y + "," + z + ")");
        assertEquals(z, VoxelKey.unpackZ(key), "Z mismatch for (" + x + "," + y + "," + z + ")");
    }
}
