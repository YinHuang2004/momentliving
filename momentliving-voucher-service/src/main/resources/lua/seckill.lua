-- KEYS must use the same {voucherId} hash tag for Redis Cluster compatibility.
-- KEYS[1] stock key, KEYS[2] user purchase-count hash, KEYS[3] reservation hash.
-- ARGV[1] userId, ARGV[2] limit, ARGV[3] orderId, ARGV[4] reservation timestamp.

local stockValue = redis.call('GET', KEYS[1])
if not stockValue then
    return -3
end

local stock = tonumber(stockValue)
if not stock or stock <= 0 then
    return -1
end

local bought = tonumber(redis.call('HGET', KEYS[2], ARGV[1]) or '0')
local limit = tonumber(ARGV[2])
if bought >= limit then
    return -2
end

redis.call('DECRBY', KEYS[1], 1)
local slot = redis.call('HINCRBY', KEYS[2], ARGV[1], 1)

-- The Redis reservation closes the crash window between pre-deduction and MQ delivery.
-- Format: userId|purchaseSlot|lastAttemptMillis|state|attemptCount
local reservation = ARGV[1] .. '|' .. slot .. '|' .. ARGV[4] .. '|PENDING|0'
redis.call('HSET', KEYS[3], ARGV[3], reservation)

return slot
