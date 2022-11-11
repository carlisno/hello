package com.lkd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.google.common.base.Strings;
import com.lkd.common.VMSystem;
import com.lkd.entity.UserEntity;
import com.lkd.http.view.TokenObject;
import com.lkd.http.vo.LoginReq;
import com.lkd.http.vo.LoginResp;
import com.lkd.service.LoginStrategyService;
import com.lkd.service.UserService;
import com.lkd.utils.BCrypt;
import com.lkd.utils.JWTUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * @Title: UserLoginStrategyServiceImpl
 * @Author YuanRL
 * @Package com.lkd.service.impl
 * @Date 2022/11/10 19:58
 * @description:
 */
@Service
public class UserLoginStrategyServiceImpl implements LoginStrategyService {
    @Autowired
    private RedisTemplate<String,String> redisTemplate;
    @Autowired
    private UserService userService;
    @Override
    public LoginResp processLogic(LoginReq req) throws IOException{
        //1.获取验证码
        LoginResp resp = new LoginResp();
        resp.setSuccess(false);
        String code =redisTemplate.boundValueOps(req.getClientToken()).get();
        //2.针对验证码进行校验
        if(Strings.isNullOrEmpty(code)){
            resp.setMsg("验证码为空");
            return resp;
        }
        if(!req.getCode().equals(code)){
            resp.setMsg("验证码错误");
            return resp;
        }
        //3.查询用户信息
        QueryWrapper<UserEntity> qw = new QueryWrapper<>();
        qw.lambda()
                .eq(UserEntity::getLoginName,req.getLoginName());
        UserEntity userEntity = userService.getOne(qw);
        //4.检验用户信息
        if(userEntity == null){
            resp.setMsg("账户名或密码错误");
            return resp;
        }
        boolean loginSuccess = BCrypt.checkpw(req.getPassword(),userEntity.getPassword());
        if(!loginSuccess){
            resp.setMsg("账户名或密码错误");
            return resp;
        }
        return okResp(userEntity, VMSystem.LOGIN_ADMIN);
    }

    @Override
    public Integer getLoginType() {
        return VMSystem.LOGIN_ADMIN;
    }

}
