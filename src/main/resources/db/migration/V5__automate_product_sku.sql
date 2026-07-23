CREATE SEQUENCE product_sku_sequence
    AS BIGINT
    INCREMENT BY 1
    MINVALUE 1
    MAXVALUE 9999999999
    START WITH 1
    NO CYCLE;

SELECT setval(
    'product_sku_sequence',
    GREATEST(
        COALESCE((
            SELECT MAX(SUBSTRING(sku FROM '^PRD-([0-9]{10})$')::BIGINT)
            FROM products
        ), 0) + 1,
        1
    ),
    FALSE
);

CREATE FUNCTION prevent_product_sku_update()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.sku IS DISTINCT FROM OLD.sku THEN
        RAISE EXCEPTION 'Product SKU cannot be changed'
            USING ERRCODE = '23514',
                  CONSTRAINT = 'ck_products_sku_immutable';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_products_sku_immutable
    BEFORE UPDATE OF sku ON products
    FOR EACH ROW
    EXECUTE FUNCTION prevent_product_sku_update();
