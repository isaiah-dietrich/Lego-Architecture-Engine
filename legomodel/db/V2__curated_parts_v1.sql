-- Curated part set v1 (40 part numbers provided by user)
-- Prereq: run V1__init_schema.sql + import_rebrickable.sql first.

BEGIN;

CREATE TABLE IF NOT EXISTS lego.curated_part_set_v1 (
    part_num TEXT PRIMARY KEY REFERENCES lego.part(part_num) ON DELETE CASCADE,
    rank_order INTEGER NOT NULL UNIQUE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    notes TEXT
);

INSERT INTO lego.curated_part_set_v1 (part_num, rank_order) VALUES
    ('3005', 1),
    ('3004', 2),
    ('3003', 3),
    ('3001', 4),
    ('3622', 5),
    ('3002', 6),
    ('3024', 7),
    ('3023', 8),
    ('3022', 9),
    ('3020', 10),
    ('3623', 11),
    ('3021', 12),
    ('3794b', 13),
    ('87580', 14),
    ('3068b', 15),
    ('3069b', 16),
    ('3070b', 17),
    ('87079', 18),
    ('15573', 19),
    ('11211', 20),
    ('30414', 21),
    ('4070', 22),
    ('87087', 23),
    ('30374', 24),
    ('2420', 25),
    ('3040b', 26),
    ('3039', 27),
    ('3044b', 28),
    ('3660', 29),
    ('3665', 30),
    ('3298', 31),
    ('3037', 32),
    ('4286', 33),
    ('85984', 34),
    ('85983', 35),
    ('11477', 36),
    ('15068', 37),
    ('29119', 38),
    ('22385', 39),
    ('3048b', 40)
ON CONFLICT (part_num) DO UPDATE
SET rank_order = EXCLUDED.rank_order;

COMMIT;

-- Joined detail view with catalog "qualifications" used by solver.
CREATE OR REPLACE VIEW lego.curated_part_details_v1 AS
SELECT
    c.rank_order,
    c.part_num,
    p.name AS part_name,
    p.part_cat_id AS category_id,
    pc.name AS category_name,
    p.part_material AS material,
    g.stud_x,
    g.stud_y,
    g.height_units,
    g.active,
    g.is_slope,
    g.slope_angle,
    g.slope_dir
FROM lego.curated_part_set_v1 c
JOIN lego.part p ON p.part_num = c.part_num
LEFT JOIN lego.part_category pc ON pc.category_id = p.part_cat_id
LEFT JOIN lego.part_geometry g ON g.part_num = c.part_num
WHERE c.enabled = TRUE
ORDER BY c.rank_order;

