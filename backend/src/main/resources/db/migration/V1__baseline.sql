-- VEYA baseline migration.
-- Intentionally creates no business tables: it establishes the Flyway history so that
-- every later schema change is a versioned migration (V2__..., V3__...).
-- Business tables follow the BA data model (Appendix B, Figure B.11) and are added per module.
SELECT 1;
