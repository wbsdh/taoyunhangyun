package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {


    @Resource
    private StringRedisTemplate stringRedisTemplate;



    @Override
    public Result queryTypeList() {
        //查询缓存中的数据
        List<ShopType> cacheTypeList = JSONUtil.toList(stringRedisTemplate.opsForValue().get(CACHE_SHOP_TYPE),ShopType.class);
        //判断是否存在数据
        if (cacheTypeList != null && cacheTypeList.size() > 0){
            //存在，返回数据
            return Result.ok(cacheTypeList);
        }
        //不存在，从数据库中查询数据
        List<ShopType> shopTypes = query().orderByAsc("sort").list();
        //存入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_TYPE,JSONUtil.toJsonStr(shopTypes));
        //返回数据
        return Result.ok(shopTypes);
    }
}
