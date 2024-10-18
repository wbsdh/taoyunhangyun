package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUserService userService;
    @Override
    public Result follow(Long followId, Boolean isFollow) {
        //获取登录用户
        UserDTO userDTO = UserHolder.getUser();
        //判断用户是否登录
        if (userDTO == null){
            return Result.ok();
        }
        Long userId = userDTO.getId();
        String key = "follows:" + userId;
        //判断关注还是取关
        if (isFollow){
            //关注，新增数据
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followId);
            boolean isSuccess = save(follow);
            if (isSuccess){
                //写入redis
                stringRedisTemplate.opsForSet().add(key,followId.toString());
            }

        }else {
            //取关，删除数据
            boolean isSuccess = remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", followId));
            //删除缓存
            if (isSuccess){
                stringRedisTemplate.opsForSet().remove(key,followId.toString());
            }

        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followId) {
        //获取登录用户
        UserDTO userDTO = UserHolder.getUser();
        //判断用户是否登录
        if (userDTO == null){
            return Result.ok();
        }
        Long userId = userDTO.getId();
        //查询是否关注
        Integer count = query().eq("user_id", userId).eq("follow_user_id", followId).count();
        return Result.ok(count > 0);
    }

    @Override
    public Result followCommons(Long id) {
        //获取登录用户
        UserDTO userDTO = UserHolder.getUser();
        //判断用户是否登录
        if (userDTO == null){
            return Result.ok();
        }
        Long userId = userDTO.getId();
        String key1 = "follows:" + userId;
        String key2 = "follows:" + id;
        //获得登录用户与该用户关注的交集
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);
        if (intersect == null || intersect.isEmpty()){
            //无交集
            return Result.ok(Collections.emptyList());
        }
        //解析id
        List<Long> ids = intersect.stream()
                .map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> userDTOS = userService.listByIds(ids).stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());
        return Result.ok(userDTOS);
    }
}
