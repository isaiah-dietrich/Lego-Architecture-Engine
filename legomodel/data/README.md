# Data Layout

- `raw/rebrickable/`: immutable source imports from Rebrickable.
  - `parts.csv`
  - `part_categories.csv`
- `staging/`: intermediate cleaning outputs and rejects.
  - `rejected_rows.csv`
- `catalog/`: solver-ready catalogs.
  - `parts_catalog_v1.csv`
  - `top_1000_catalog_v1.csv`
- `usage/`: usage/ranking artifacts and unmatched joins.
  - `top_1000_unmatched.csv`

Guideline: keep `raw/` read-only and write new transforms into `staging/` or `catalog/`.
