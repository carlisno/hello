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
public class EmpLoginStrategyServiceImpl implements LoginStrategyService {
    @Autowired
    private RedisTemplate<String,String> redisTemplate;
    @Autowired
    private UserService userService;
    @Override
    public LoginResp processLogic(LoginReq req) throws IOException {
        //1.校验验证码是否正确
        LoginResp resp = new LoginResp();
        resp.setSuccess(false);
        String code =redisTemplate.boundValueOps(req.getMobile()).get();
        if(Strings.isNullOrEmpty(code)){
            resp.setMsg("验证码为空");
            return resp;
        }
        if(!req.getCode().equals(code)){
            resp.setMsg("验证码错误");
            return resp;
        }

        //2.检验手机号是否存在
        QueryWrapper<UserEntity> qw = new QueryWrapper<>();
        qw.lambda()
                .eq(UserEntity::getMobile, req.getMobile());
        UserEntity userEntity = userService.getOne(qw);
        if (userEntity == null){
            resp.setMsg("不存在该账户");
            return resp;
        }

        //3.组装返回数据
        return okResp(userEntity, VMSystem.LOGIN_EMP );
    }

    @Override
    public Integer getLoginType() {
        return VMSystem.LOGIN_EMP;
    }
}
