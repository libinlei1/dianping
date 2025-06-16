local key = KEYS[1]
local userId = ARGV[1]

-- 检查用户是否已经点赞
local isMember = redis.call('SISMEMBER', key, userId)

if isMember == 0 then
    -- 用户未点赞，执行点赞操作
    redis.call('SADD', key, userId)
    return 1 -- 返回 1 表示点赞成功
else
    -- 用户已点赞，执行取消点赞操作
    redis.call('SREM', key, userId)
    return 0 -- 返回 0 表示取消点赞成功
end