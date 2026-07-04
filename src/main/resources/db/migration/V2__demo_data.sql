-- Sample data for the demo schema.

INSERT INTO customers (name, email, country) VALUES ('Asha Rao',      'asha@example.com',   'India');
INSERT INTO customers (name, email, country) VALUES ('Lukas Meyer',   'lukas@example.com',  'Germany');
INSERT INTO customers (name, email, country) VALUES ('Emma Johnson',  'emma@example.com',   'United States');
INSERT INTO customers (name, email, country) VALUES ('Kenji Tanaka',  'kenji@example.com',  'Japan');

INSERT INTO products (name, category, unit_price) VALUES ('Mechanical Keyboard', 'Electronics', 89.99);
INSERT INTO products (name, category, unit_price) VALUES ('USB-C Dock',          'Electronics', 129.50);
INSERT INTO products (name, category, unit_price) VALUES ('Standing Desk',       'Furniture',   399.00);
INSERT INTO products (name, category, unit_price) VALUES ('Desk Lamp',           'Furniture',   45.25);
INSERT INTO products (name, category, unit_price) VALUES ('Noise-Cancel Headset','Electronics', 199.00);

INSERT INTO orders (customer_id, order_date, status) VALUES (1, SYSDATE - 45, 'DELIVERED');
INSERT INTO orders (customer_id, order_date, status) VALUES (1, SYSDATE - 10, 'SHIPPED');
INSERT INTO orders (customer_id, order_date, status) VALUES (2, SYSDATE - 30, 'DELIVERED');
INSERT INTO orders (customer_id, order_date, status) VALUES (3, SYSDATE - 5,  'NEW');
INSERT INTO orders (customer_id, order_date, status) VALUES (4, SYSDATE - 80, 'DELIVERED');
INSERT INTO orders (customer_id, order_date, status) VALUES (3, SYSDATE - 2,  'NEW');

INSERT INTO order_items (order_id, product_id, quantity, unit_price) VALUES (1, 1, 1, 89.99);
INSERT INTO order_items (order_id, product_id, quantity, unit_price) VALUES (1, 4, 2, 45.25);
INSERT INTO order_items (order_id, product_id, quantity, unit_price) VALUES (2, 5, 1, 199.00);
INSERT INTO order_items (order_id, product_id, quantity, unit_price) VALUES (3, 3, 1, 399.00);
INSERT INTO order_items (order_id, product_id, quantity, unit_price) VALUES (4, 2, 1, 129.50);
INSERT INTO order_items (order_id, product_id, quantity, unit_price) VALUES (5, 1, 2, 89.99);
INSERT INTO order_items (order_id, product_id, quantity, unit_price) VALUES (5, 2, 1, 129.50);
INSERT INTO order_items (order_id, product_id, quantity, unit_price) VALUES (6, 4, 3, 45.25);
