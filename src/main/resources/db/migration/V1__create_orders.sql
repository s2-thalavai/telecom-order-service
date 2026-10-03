CREATE TABLE customer_orders (
    id             BINARY(16)     NOT NULL,
    customer_name  VARCHAR(100)   NOT NULL,
    email          VARCHAR(150)   NOT NULL,
    plan_code      VARCHAR(20)    NOT NULL,
    monthly_price  DECIMAL(10, 2) NOT NULL,
    circle         VARCHAR(3)     NOT NULL,
    msisdn         VARCHAR(15)    NULL,
    iccid          VARCHAR(22)    NULL,
    status         VARCHAR(20)    NOT NULL,
    credit_score   INT            NULL,
    reason         VARCHAR(255)   NULL,
    created_at     DATETIME(6)    NOT NULL,
    updated_at     DATETIME(6)    NULL,
    version        BIGINT         NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_orders_status (status),
    INDEX idx_orders_email (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
