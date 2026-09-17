-- ============================================================
-- Supabase / PostgreSQL schema for Modular Monolith Lab 2
-- Run this script once in the Supabase SQL Editor to recreate
-- the schema from scratch with seed data.
-- ============================================================

-- Drop existing tables in reverse dependency order
DROP TABLE IF EXISTS notifications CASCADE;
DROP TABLE IF EXISTS order_items CASCADE;
DROP TABLE IF EXISTS orders CASCADE;
DROP TABLE IF EXISTS inventory CASCADE;

-- 1. Inventory table
CREATE TABLE inventory (
    product_id VARCHAR(10)  PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    stock      INT          NOT NULL CHECK (stock >= 0)
);

-- 2. Orders table (supports CONFIRMED, REJECTED, CANCELLED)
CREATE TABLE orders (
    order_id   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    status     VARCHAR(20)  NOT NULL,
    reason     TEXT,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 3. Order Items table (multi-item orders)
CREATE TABLE order_items (
    item_id    UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id   UUID        NOT NULL REFERENCES orders(order_id) ON DELETE CASCADE,
    product_id VARCHAR(10) NOT NULL REFERENCES inventory(product_id),
    quantity   INT         NOT NULL CHECK (quantity > 0)
);

-- 4. Notifications table (event-driven notification module)
CREATE TABLE notifications (
    notification_id UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    message         TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 5. Seed data (idempotent)
INSERT INTO inventory (product_id, name, stock) VALUES
    ('P100', 'Wireless Mouse',      25),
    ('P200', 'Mechanical Keyboard', 10),
    ('P300', 'USB-C Hub',            0)
ON CONFLICT (product_id) DO UPDATE SET
    name = EXCLUDED.name,
    stock = EXCLUDED.stock;
