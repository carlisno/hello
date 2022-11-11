package com.lkd.config;

import com.lkd.service.LoginStrategyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * @Title: LoginStrategyConfig
 * @Author YuanRL
 * @Package com.lkd.config
 * @Date 2022/11/10 20:36
 * @description: spring MVC 提供的一个钩子函数setApplicationContext,让spring帮我们管理Bean
 */
@Component
@Slf4j
public class LoginStrategyConfig implements ApplicationContextAware {

    //key:登录类型 value:bean
    private Map<Integer,LoginStrategyService> serviceMap = new HashMap<>();

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        //获取Bean名称,默认是类名(首字母小写)
        Map<String, LoginStrategyService> beansOfType =
                applicationContext.getBeansOfType(LoginStrategyService.class);

        beansOfType.values().forEach(each->{
            serviceMap.put(each.getLoginType(), each);
        });
        log.info("setApplicationContext");
    }

    /**
     * 通过登录类型获取执行逻辑
     * @param loginType
     * @return 具体的执行Bean
     */
    public LoginStrategyService getLoginStrategyByLoginType(Integer loginType){
        return serviceMap.get(loginType);
    }
}
