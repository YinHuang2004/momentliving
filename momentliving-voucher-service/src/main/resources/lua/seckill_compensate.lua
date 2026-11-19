-- Atomic and idempotent compensation.
-- KEYS[1] stock, KEYS[2] count hash, KEYS[3] reservation hash, KEYS[4] compensated marker.
-- ARGV[1] userId, ARGV[2] orderId, ARGV[3] marker ttl seconds.

if redis.call('SET', KEYS[4], '1', 'NX', 'EX', ARGV[3]) == false then
    return 0
end

redis.call('INCRBY', KEYS[1], 1)
local count = tonumber(redis.call('HINCRBY', KEYS[2], ARGV[1], -1))
if count < 0 then
    redis.call('HSET', KEYS[2], ARGV[1], 0)
end
redis.call('HDEL', KEYS[3], ARGV[2])

return 1
