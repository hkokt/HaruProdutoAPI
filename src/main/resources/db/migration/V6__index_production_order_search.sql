CREATE INDEX idx_production_orders_created_id
    ON production_orders (created_at DESC, id DESC);

CREATE INDEX idx_production_orders_status_created_id
    ON production_orders (status, created_at DESC, id DESC);
