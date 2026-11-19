-- Update an existing reservation without recreating a record already removed by a consumer.
-- KEYS[1] reservation hash; ARGV[1] orderId; ARGV[2] serialized reservation.
if redis.call('HEXISTS', KEYS[1], ARGV[1]) == 0 then
    return 0
end
redis.call('HSET', KEYS[1], ARGV[1], ARGV[2])
return 1
