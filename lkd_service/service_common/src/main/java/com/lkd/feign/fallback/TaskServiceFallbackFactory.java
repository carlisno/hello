package com.lkd.feign.fallback;

import com.lkd.feign.TaskService;
import com.lkd.vo.UserWorkVO;
import feign.hystrix.FallbackFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class TaskServiceFallbackFactory implements FallbackFactory<TaskService> {
    @Override
    public TaskService create(Throwable throwable) {
        log.error("调用工单服务失败",throwable);

        return new TaskService() {

            @Override
            public UserWorkVO getUserWork(Integer userId, String start, String end) {
                UserWorkVO userWork = new UserWorkVO();
                userWork.setCancelCount(0);
                userWork.setProgressTotal(0);
                userWork.setWorkCount(0);
                userWork.setUserId(userId);
                return userWork;
            }

        };
    }
}
