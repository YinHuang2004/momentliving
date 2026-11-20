-- 已有数据库仅执行一次。执行前请先备份，并确认列尚不存在。
-- 背景：2026-11-20 排查"Redis 秒杀库存超补"事故后，将补偿幂等凭据的真源从 Redis
-- （seckill:compensated:{voucherId}:{orderId}，随重启丢失）移到 MySQL 本列：
-- 补偿执行前先 CAS 置位（0→1），影响行数=1 才执行 Lua 回补；
-- Redis 数据丢失后兜底任务重放会被本标记挡住，避免与对账任务的 key 重建叠加超补。
ALTER TABLE voucher_order
    ADD COLUMN redis_compensated TINYINT NOT NULL DEFAULT 0
        COMMENT 'Redis补偿标记：0未回补Redis/1已回补，补偿前CAS置位防重放' AFTER status;
