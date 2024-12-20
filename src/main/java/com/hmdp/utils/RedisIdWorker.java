package com.hmdp.utils;


import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {

    private static final long BEGIN_TIMESTAMP = 1704067200L;
    private static final int COUNT_BITS = 32;

    private StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;
    }
    public long nextId(){
//        //生成时间戳
//
//        LocalDateTime now = LocalDateTime.now();
//        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
//        long timestamp = nowSecond - BEGIN_TIMESTAMP;
//        //生成序列号
//        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
//        Long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);
//        //拼接并返回
//        return timestamp << COUNT_BITS | count;
        //采用雪花算法生成订单id
        Snowflake snowflake = IdUtil.createSnowflake(0, 1);
        long id = snowflake.nextId();
        return id;
    }


}
