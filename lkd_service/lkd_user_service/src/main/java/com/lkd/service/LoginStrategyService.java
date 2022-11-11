package com.lkd.service;

import com.lkd.common.VMSystem;
import com.lkd.entity.UserEntity;
import com.lkd.http.view.TokenObject;
import com.lkd.http.vo.LoginReq;
import com.lkd.http.vo.LoginResp;
import com.lkd.utils.JWTUtil;

import java.io.IOException;

/**
 * @Title: LoginStrategyService
 * @Author YuanRL
 * @Package com.lkd.service
 * @Date 2022/11/10 19:55
 * @description: 登录策略服务
 */
public interface LoginStrategyService {

    /**
     * 执行登录策略
     * @param req
     * @return
     */
    LoginResp processLogic(LoginReq req) throws IOException;

    /**
     * 获取登录类型
     * @return 登录类型对应的枚举值
     */
    Integer getLoginType();
    /**
     * 进行参数返回结果的封装
     * @param userEntity
     * @param loginType
     * @return
     * @throws IOException
     */
    default LoginResp okResp(UserEntity userEntity, Integer loginType ) throws IOException {
        LoginResp resp = new LoginResp();
        resp.setSuccess(true);
        resp.setRoleCode(userEntity.getRoleCode());
        resp.setUserName(userEntity.getUserName());
        resp.setUserId(userEntity.getId());
        resp.setRegionId(userEntity.getRegionId()+"");
        resp.setMsg("登录成功");

        TokenObject tokenObject = new TokenObject();
        tokenObject.setUserId(userEntity.getId());
        tokenObject.setMobile(userEntity.getMobile());
        tokenObject.setLoginType(loginType);
        String token = JWTUtil.createJWTByObj(tokenObject,userEntity.getMobile() + VMSystem.JWT_SECRET);
        resp.setToken(token);
        return resp;
    }
}
