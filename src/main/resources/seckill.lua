--参数列表
--KEYS[1] 门票id

local voucherId = ARGV[1]
--local voucherId = 13

--用户id
local userId = ARGV[2]
--local userId = 1010
local orderId = ARGV[3]

--数据key
--库存key
local stockKey = 'seckill:stock:' .. voucherId
--订单key
local orderKey = 'seckill:order:' .. voucherId

--判断库存是否充足
if (tonumber(redis.call('get',stockKey)) <= 0) then
    --库存不足
    return 1
end
--判断用户是否下单 sismember
if (redis.call('sismember',orderKey,userId) == 1) then
    --用户存在
    return 2
end
--验证通过
-- 3.4.扣库存 incrby stockKey -1
redis.call('incrby', stockKey, -1)
-- 3.5.下单（保存用户）sadd orderKey userId
redis.call('sadd', orderKey, userId)
-- 3.6.发送消息到队列中， XADD stream.orders * k1 v1 k2 v2 ...
redis.call('xadd', 'stream.orders', '*', 'userId', userId, 'voucherId', voucherId, 'id', orderId)
return 0