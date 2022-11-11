package com.lkd.service.impl;

import com.lkd.common.VMSystem;
import com.lkd.http.vo.LoginReq;
import com.lkd.http.vo.LoginResp;
import com.lkd.service.LoginStrategyService;
import com.lkd.service.PartnerService;
import org.springframework.beans.factory.annotation.Autowired;
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
public class PartnerLoginStrategyServiceImpl implements LoginStrategyService {
    @Autowired
    private PartnerService partnerService;
    @Override
    public LoginResp processLogic(LoginReq req) throws IOException {
        return partnerService.login(req);
    }

    @Override
    public Integer getLoginType() {
        return VMSystem.LOGIN_PARTNER;
    }
}
