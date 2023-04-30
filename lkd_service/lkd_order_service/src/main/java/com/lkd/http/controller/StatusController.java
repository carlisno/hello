package com.lkd.http.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/status")
@Slf4j
public class StatusController {


    /**
     * todo: 状态top10之前路径localhost/likede/api/status-service/status/top10
     */
    @GetMapping("top10")
    public void statusTop10(){
        return;
    }
}
