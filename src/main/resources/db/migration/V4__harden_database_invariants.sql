DROP INDEX idx_inventory_lots_product_expiration_id;

CREATE INDEX idx_inventory_lots_fefo
    ON inventory_lots (
        product_id,
        status,
        expiration_date ASC NULLS LAST,
        id ASC
    );

ALTER TABLE products
    ADD CONSTRAINT ck_products_product_type
        CHECK (product_type IN (
            'RAW_MATERIAL',
            'COMPONENT',
            'INTERMEDIATE_PRODUCT',
            'FINISHED_PRODUCT',
            'KIT',
            'SERVICE'
        )),
    ADD CONSTRAINT ck_products_default_measurement_unit
        CHECK (default_measurement_unit IN (
            'UNIT',
            'KILOGRAM',
            'GRAM',
            'LITER',
            'MILLILITER',
            'METER',
            'CENTIMETER',
            'SQUARE_METER',
            'CUBIC_METER',
            'BOX',
            'PACKAGE'
        ));

ALTER TABLE product_compositions
    ADD CONSTRAINT ck_product_compositions_measurement_unit
        CHECK (measurement_unit IN (
            'UNIT',
            'KILOGRAM',
            'GRAM',
            'LITER',
            'MILLILITER',
            'METER',
            'CENTIMETER',
            'SQUARE_METER',
            'CUBIC_METER',
            'BOX',
            'PACKAGE'
        ));

ALTER TABLE inventory_lots
    ADD CONSTRAINT ck_inventory_lots_status
        CHECK (status IN (
            'AVAILABLE',
            'DEPLETED',
            'EXPIRED',
            'BLOCKED'
        ));

ALTER TABLE inventory_movements
    ADD CONSTRAINT ck_inventory_movements_type
        CHECK (movement_type IN (
            'ENTRY',
            'EXIT',
            'ADJUSTMENT_IN',
            'ADJUSTMENT_OUT',
            'PRODUCTION_CONSUMPTION',
            'PRODUCTION_ENTRY'
        ));

ALTER TABLE production_orders
    ADD CONSTRAINT ck_production_orders_status
        CHECK (status IN (
            'CREATED',
            'IN_PROGRESS',
            'COMPLETED',
            'CANCELLED'
        )),
    ADD CONSTRAINT ck_production_orders_lifecycle
        CHECK (
            (status = 'CREATED'
                AND started_at IS NULL
                AND completed_at IS NULL)
            OR (status = 'IN_PROGRESS'
                AND started_at IS NOT NULL
                AND completed_at IS NULL)
            OR (status = 'COMPLETED'
                AND started_at IS NOT NULL
                AND completed_at IS NOT NULL)
            OR (status = 'CANCELLED'
                AND completed_at IS NULL)
        );

ALTER TABLE production_consumptions
    ADD CONSTRAINT ck_production_consumptions_measurement_unit
        CHECK (measurement_unit IN (
            'UNIT',
            'KILOGRAM',
            'GRAM',
            'LITER',
            'MILLILITER',
            'METER',
            'CENTIMETER',
            'SQUARE_METER',
            'CUBIC_METER',
            'BOX',
            'PACKAGE'
        ));

CREATE FUNCTION reject_inventory_movement_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'inventory_movements is append-only'
        USING ERRCODE = '55000';
    RETURN NULL;
END;
$$;

CREATE TRIGGER trg_inventory_movements_append_only
    BEFORE UPDATE OR DELETE ON inventory_movements
    FOR EACH ROW
    EXECUTE FUNCTION reject_inventory_movement_mutation();
