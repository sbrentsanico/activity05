-- ============================================================
-- Supabase / PostgreSQL schema for Modular Monolith Lab
-- Run this script once in the Supabase SQL Editor
-- ============================================================

-- 1. Inventory table
CREATE TABLE IF NOT EXISTS inventory (
    product_id VARCHAR(10)  PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    stock      INT          NOT NULL CHECK (stock >= 0)
);

-- 2. Seed data (idempotent)
INSERT INTO inventory (product_id, name, stock) VALUES
    ('P100', 'Wireless Mouse',      25),
    ('P200', 'Mechanical Keyboard', 10),
    ('P300', 'USB-C Hub',            0)
ON CONFLICT (product_id) DO NOTHING;

-- 3. Orders table
CREATE TABLE IF NOT EXISTS orders (
    order_id   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id VARCHAR(10)  NOT NULL,
    quantity   INT          NOT NULL,
    status     VARCHAR(20)  NOT NULL,
    reason     TEXT,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
