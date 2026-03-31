package com.lego.cli;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class CliOptionsParserGeometryFlagsTest {

    @Test
    void parse_readsGeometryFlagValues() {
        ParsedOptions parsed = CliOptionsParser.parse(new String[] {
            "model.obj",
            "20",
            "--ldraw-library-dir=/tmp/ldraw",
            "--geometry-mask-cache-dir=/tmp/cache"
        });

        assertEquals(Path.of("/tmp/ldraw"), parsed.ldrawLibraryDir());
        assertEquals(Path.of("/tmp/cache"), parsed.geometryMaskCacheDir());
    }

}
