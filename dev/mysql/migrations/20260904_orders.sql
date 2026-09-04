USE goods_service;

CREATE TABLE IF NOT EXISTS goods_order (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_no VARCHAR(40) NOT NULL,
    goods_id INT NOT NULL,
    goods_name VARCHAR(100) NOT NULL,
    goods_image VARCHAR(255) NOT NULL,
    buyer_id VARCHAR(32) NOT NULL,
    seller_id VARCHAR(32) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    postage DECIMAL(10,2) NOT NULL DEFAULT 0,
    delivery_method VARCHAR(16) NOT NULL,
    address_name VARCHAR(40),
    address_phone VARCHAR(24),
    address_region VARCHAR(120),
    address_detail VARCHAR(255),
    request_id VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    tracking_no VARCHAR(80),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paid_at DATETIME,
    shipped_at DATETIME,
    completed_at DATETIME,
    active_goods_id INT GENERATED ALWAYS AS (CASE WHEN status <> 'CANCELLED' THEN goods_id ELSE NULL END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    UNIQUE KEY uk_buyer_request (buyer_id, request_id),
    UNIQUE KEY uk_active_goods (active_goods_id),
    KEY idx_goods_status (goods_id, status),
    KEY idx_buyer_status (buyer_id, status, id),
    KEY idx_seller_status (seller_id, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS goods_order_review (
    order_id BIGINT NOT NULL,
    buyer_id VARCHAR(32) NOT NULL,
    seller_id VARCHAR(32) NOT NULL,
    rating TINYINT NOT NULL,
    content VARCHAR(1000) NOT NULL DEFAULT '',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (order_id),
    KEY idx_review_seller (seller_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
