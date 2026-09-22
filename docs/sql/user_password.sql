-- ============================================================
-- 用户密码功能：user 表新增 password 列（2026-09-11）
-- 执行库：localhost:3306/momentliving（用户库）
-- 幂等：MySQL 8 无 ADD COLUMN IF NOT EXISTS，重复执行会报
--       Duplicate column name 'password'，忽略即可（说明已加过）
-- ============================================================

ALTER TABLE `user`
    ADD COLUMN `password` VARCHAR(100) NULL DEFAULT NULL COMMENT '密码（BCrypt 哈希，60字符）；NULL=未设置密码（纯验证码登录用户）';

-- 说明：
-- 1. 存量用户 password 均为 NULL：首次调用 PUT /user/password 不需要原密码（视为"设置密码"）；
-- 2. BCrypt 哈希固定 60 字符，VARCHAR(100) 留了余量；
-- 3. 修改/找回密码成功后，user-service 会删除 Redis key login:refresh:{userId}，
--    网关 AuthGlobalFilter 回查不到该键即返回 401，实现全端强制下线。
