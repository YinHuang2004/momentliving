-- 已有数据库仅执行一次。执行前请先备份，并确认相关列、约束和索引尚不存在。
ALTER TABLE voucher_order
    ADD COLUMN purchase_slot TINYINT UNSIGNED NULL COMMENT '秒杀限购槽位，普通订单为空' AFTER voucher_id;

UPDATE voucher_order vo
JOIN (
    SELECT ranked.id,
           IF(ranked.slot_number BETWEEN 1 AND 3, ranked.slot_number, NULL) AS purchase_slot
    FROM (
        SELECT vo2.id,
               ROW_NUMBER() OVER (
                   PARTITION BY vo2.user_id, vo2.voucher_id
                   ORDER BY vo2.create_time, vo2.id
               ) AS slot_number
        FROM voucher_order vo2
        JOIN voucher v ON v.id = vo2.voucher_id AND v.type = 1
    ) ranked
) slots ON slots.id = vo.id
SET vo.purchase_slot = slots.purchase_slot
WHERE vo.purchase_slot IS NULL;

ALTER TABLE voucher_order
    ADD CONSTRAINT chk_voucher_order_purchase_slot
        CHECK (purchase_slot IS NULL OR purchase_slot BETWEEN 1 AND 3),
    ADD UNIQUE KEY uk_user_voucher_slot (user_id, voucher_id, purchase_slot),
    ADD KEY idx_voucher_order_status_created (status, create_time),
    ADD KEY idx_voucher_order_user_status (user_id, status, create_time),
    ADD KEY idx_voucher_order_voucher_status (voucher_id, status);

ALTER TABLE payment
    ADD UNIQUE KEY uk_payment_transaction_id (transaction_id);

ALTER TABLE seckill_voucher
    ADD KEY idx_seckill_voucher_time (begin_time, end_time),
    ADD KEY idx_seckill_voucher_end_stock (end_time, stock);
