package com.hmdp.utils;


import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;


@Slf4j
@Component
public class CacheClient {
    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit){
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <R,ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallback,Long time, TimeUnit unit) throws InterruptedException {

        String key = keyPrefix + id;

        //1.查询redis中是否有数据
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if (!StrUtil.isBlank(json)){
            //存在，返回数据
            return JSONUtil.toBean(json, type);
        }
        //命中的为空值
        if (json != null){
            //返回错误信息
            return null;
        }
        //3.不存在，在数据库中查找
        R r = dbFallback.apply(id);
        //判断数据库中是否存在
        if (r == null){
            //不存在，返回错误,将空值写入redis，解决缓存穿透
            stringRedisTemplate.opsForValue().set(key,"",time, unit);

            return null;
        }
        //存在，将数据存入redis
        this.set(key,r,time,unit);
        //返回数据
        return r;
    }
    public <R,ID> R queryWithLogicalExpire(String keyPrefix,ID id,Class<R> type,Function<ID,R> dbFallback,Long time, TimeUnit unit) throws InterruptedException {

        String key = keyPrefix + id;
        //1.查询redis中是否有数据
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if (StrUtil.isBlank(json)){
            //存在，返回数据
            return null;
        }
        // 命中，先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        R r = JSONUtil.toBean(data, type);
        LocalDateTime expireTime = redisData.getExpireTime();
        //需要判断过期时间
        if (expireTime.isAfter(LocalDateTime.now())){
            //未过期，直接返回
            return r;
        }
        //已过期，需要缓存重建
        //获取锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        //判断是否获取锁成功
        if (isLock){
            //成功获取，开启线程
            try {
                //查询数据库
                R r1 = dbFallback.apply(id);
                //写入redis
                this.setWithLogicalExpire(key,r1,time,unit);
            }catch (Exception e){
                throw new RuntimeException(e);
            }finally {
                unLock(lockKey);
            }
        }
        //返回旧数据
        return r;
    }

    //获取锁
    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    //释放锁
    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }

}
