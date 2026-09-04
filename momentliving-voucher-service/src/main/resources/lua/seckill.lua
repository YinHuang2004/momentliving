-- 1.参数列表
local voucherId = ARGV[1]
local userId = ARGV[2]
local limit = tonumber(ARGV[3])


-- 2.数据key
local stockKey = 'seckill:stock:' .. voucherId
local countKey = 'seckill:count:' .. voucherId

-- 3.业务逻辑
-- 判断库存是否充足
local stock = tonumber(redis.call('get', stockKey))
if (stock <= 0) then
    return 1
end

-- 判断用户下单次数是否超过限制
local bought = tonumber(redis.call('hget', countKey, userId) or '0')
if (bought >= limit) then
    return 2
end


-- 扣库存
redis.call('decrby', stockKey, 1)

-- 下单（记录用户）
redis.call('hincrby', countKey, userId, 1)

return 0