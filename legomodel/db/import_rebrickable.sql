-- Import script for local Rebrickable CSV snapshots.
-- Run from psql with:
--   psql -h localhost -U lego_user -d lego_engine -f legomodel/db/import_rebrickable.sql

BEGIN;

TRUNCATE TABLE lego.part_color_availability;
TRUNCATE TABLE lego.part_geometry;
TRUNCATE TABLE lego.part;
TRUNCATE TABLE lego.part_category;
TRUNCATE TABLE lego.color;

COMMIT;

-- Categories
\copy lego.part_category(category_id, name)
FROM 'legomodel/data/raw/rebrickable/part_categories.csv'
WITH (FORMAT csv, HEADER true);

-- Parts
\copy lego.part(part_num, name, part_cat_id, part_material)
FROM 'legomodel/data/raw/rebrickable/parts.csv'
WITH (FORMAT csv, HEADER true);

-- Colors
\copy lego.color(color_id, name, rgb_hex, is_transparent)
FROM 'legomodel/data/raw/rebrickable/colors.csv'
WITH (FORMAT csv, HEADER true);

-- Optional curated geometry import from your cleaned catalog.
-- This uses rows from parts_catalog_v1.csv if present.
CREATE TEMP TABLE _geometry_staging (
    part_id TEXT,
    name TEXT,
    category TEXT,
    stud_x INTEGER,
    stud_y INTEGER,
    height_units TEXT,
    material TEXT,
    active BOOLEAN
);

\copy _geometry_staging
FROM 'legomodel/data/catalog/parts_catalog_v1.csv'
WITH (FORMAT csv, HEADER true);

INSERT INTO lego.part_geometry (part_num, stud_x, stud_y, height_units, active)
SELECT s.part_id, s.stud_x, s.stud_y, s.height_units, COALESCE(s.active, TRUE)
FROM _geometry_staging s
JOIN lego.part p ON p.part_num = s.part_id
ON CONFLICT (part_num) DO UPDATE
SET stud_x = EXCLUDED.stud_x,
    stud_y = EXCLUDED.stud_y,
    height_units = EXCLUDED.height_units,
    active = EXCLUDED.active;
