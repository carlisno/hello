package com.lkd.feign;

import com.lkd.feign.fallback.TaskServiceFallbackFactory;
import com.lkd.vo.UserWorkVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;


@FeignClient(value = "task-service",fallbackFactory = TaskServiceFallbackFactory.class)
public interface TaskService {

    @GetMapping("/task/userWork")
    UserWorkVO getUserWork(@RequestParam Integer userId, @RequestParam String start, @RequestParam String end);


}
