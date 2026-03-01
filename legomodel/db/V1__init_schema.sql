-- LEGO Architecture Engine
-- PostgreSQL v1 schema

BEGIN;

CREATE SCHEMA IF NOT EXISTS lego;

-- Master part categories from Rebrickable.
CREATE TABLE IF NOT EXISTS lego.part_category (
    category_id INTEGER PRIMARY KEY,
    name TEXT NOT NULL
);

-- Master part catalog.
CREATE TABLE IF NOT EXISTS lego.part (
    part_num TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    part_cat_id INTEGER REFERENCES lego.part_category(category_id),
    part_material TEXT
);

-- Canonical geometry used by the solver (small curated subset first).
CREATE TABLE IF NOT EXISTS lego.part_geometry (
    part_num TEXT PRIMARY KEY REFERENCES lego.part(part_num) ON DELETE CASCADE,
    stud_x INTEGER NOT NULL CHECK (stud_x > 0),
    stud_y INTEGER NOT NULL CHECK (stud_y > 0),
    height_units TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    is_slope BOOLEAN NOT NULL DEFAULT FALSE,
    slope_angle NUMERIC(5,2),
    slope_dir TEXT,
    CHECK (slope_dir IN ('+x', '-x', '+y', '-y') OR slope_dir IS NULL)
);

-- Rebrickable colors.
CREATE TABLE IF NOT EXISTS lego.color (
    color_id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    rgb_hex CHAR(6) NOT NULL,
    is_transparent BOOLEAN NOT NULL DEFAULT FALSE
);

-- Optional many-to-many availability (can remain sparse early on).
CREATE TABLE IF NOT EXISTS lego.part_color_availability (
    part_num TEXT NOT NULL REFERENCES lego.part(part_num) ON DELETE CASCADE,
    color_id INTEGER NOT NULL REFERENCES lego.color(color_id) ON DELETE CASCADE,
    available BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (part_num, color_id)
);

CREATE INDEX IF NOT EXISTS idx_part_geometry_dims
    ON lego.part_geometry (stud_x, stud_y, active);

CREATE INDEX IF NOT EXISTS idx_part_cat
    ON lego.part (part_cat_id);

COMMIT;
