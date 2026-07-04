-- Demo schema so /api/v1/query works out of the box.
-- Replace or extend with your own migrations (V2__..., V3__...).

CREATE TABLE customers (
    customer_id   NUMBER(10)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name          VARCHAR2(100) NOT NULL,
    email         VARCHAR2(255) NOT NULL UNIQUE,
    country       VARCHAR2(60),
    created_at    DATE          DEFAULT SYSDATE NOT NULL
);
COMMENT ON TABLE customers IS 'Registered customers';
COMMENT ON COLUMN customers.country IS 'Customer country name, e.g. India, Germany';

CREATE TABLE products (
    product_id    NUMBER(10)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name          VARCHAR2(120) NOT NULL,
    category      VARCHAR2(60)  NOT NULL,
    unit_price    NUMBER(10,2)  NOT NULL
);
COMMENT ON TABLE products IS 'Product catalog';

CREATE TABLE orders (
    order_id      NUMBER(10)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id   NUMBER(10)    NOT NULL REFERENCES customers (customer_id),
    order_date    DATE          DEFAULT SYSDATE NOT NULL,
    status        VARCHAR2(20)  DEFAULT 'NEW' NOT NULL
);
COMMENT ON TABLE orders IS 'Customer orders';
COMMENT ON COLUMN orders.status IS 'NEW, SHIPPED, DELIVERED or CANCELLED';

CREATE TABLE order_items (
    order_item_id NUMBER(10)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id      NUMBER(10)    NOT NULL REFERENCES orders (order_id),
    product_id    NUMBER(10)    NOT NULL REFERENCES products (product_id),
    quantity      NUMBER(5)     NOT NULL,
    unit_price    NUMBER(10,2)  NOT NULL
);
COMMENT ON TABLE order_items IS 'Line items belonging to an order';
