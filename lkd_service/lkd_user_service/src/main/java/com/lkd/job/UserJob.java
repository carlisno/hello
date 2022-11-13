package com.lkd.job;

import com.lkd.common.VMSystem;
import com.lkd.service.UserService;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * @Title: UserJob
 * @Author YuanRL
 * @Package com.lkd.job
 * @Date 2022/11/12 22:25
 * @description:
 */
@Component
@Slf4j
public class UserJob {

    @Autowired
    private UserService userService;

    @Autowired
    private RedisTemplate redisTemplate;

    @XxlJob("workCountInitJobHandler")
    public ReturnT<String> workCountInitJobHandler(String param) {
        //初始化分数
        userService.list().stream()
                .filter(each -> !Objects.equals(each.getRoleCode(), VMSystem.ADMIN_ROLE))
                .forEach(user -> {
                    String redisKey = VMSystem.REGION_TASK_KEY_PREF
                            + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                            + "." + user.getRegionId()
                            + "." + user.getRoleCode();

                    redisTemplate.opsForZSet().add(redisKey,user.getId(),0);
                    redisTemplate.expire(redisKey, Duration.ofDays(1));
                });

        return ReturnT.SUCCESS;
    }


}