-- V1__baseline.sql
-- Luci Foundation Walking Skeleton — infrastructure scaffolding only.
-- NO domain tables. NO currency columns. (FR-014, Constitution §I/§VI)
-- This migration is additive only — no DROP COLUMN or type-narrowing.

CREATE EXTENSION IF NOT EXISTS vector;

COMMENT ON TABLE flyway_schema_history IS
    'Luci baseline migration applied. Walking skeleton — no domain tables yet.';
