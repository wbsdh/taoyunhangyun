package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校验手机号
        if (!RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("草泥马，手机输错了");
        }
        //生成验证码
        String code = RandomUtil.randomNumbers(6);
        //保存到session
        session.setAttribute("code",code);
        //发送验证码
        //todo 后面做
        log.debug("发送短信验证码超成功，验证码:{"+code+"}");
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //校验手机号
        if (!RegexUtils.isPhoneInvalid(loginForm.getPhone())) {
            return Result.fail("草泥马，手机输错了");
        }
        //校验验证码
        String cacheCode = (String) session.getAttribute("code");
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.equals(code)){
            return Result.fail("验证码输入错误");
        }
        //查询用户是否存在
        User user = query().eq("phone", loginForm.getPhone()).one();
        //判断用户是否存在
        if (user == null){
            //用户不存在，创建用户，并保存到数据库
            user = createUserWithPhone(loginForm.getPhone());
        }
        //保存到session
        session.setAttribute("user",user);

        return Result.ok();
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX+RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
