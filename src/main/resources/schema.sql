-- The AI's suggestion per product code and its description and NCM, so a repeated product is not paid for again.
CREATE TABLE IF NOT EXISTS ai_suggestions (
    product_code  text        NOT NULL,
    input_hash    char(64)    NOT NULL,
    cst           text        NOT NULL,
    c_class_trib  text        NOT NULL,
    confidence    numeric     NOT NULL,
    justification text        NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (product_code, input_hash)
);
