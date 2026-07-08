package com.macro.mall.recommend.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.recommend.repository.RecommendRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/recommend")
public class RecommendController {
    @Autowired
    private RecommendRepository recommendRepository;

    @GetMapping("/hot")
    public CommonResult<List<Map<String, Object>>> hot(@RequestParam(defaultValue = "10") Integer limit) {
        return CommonResult.success(recommendRepository.hot(limit));
    }

    @GetMapping("/user/{userId}")
    public CommonResult<List<Map<String, Object>>> userRecommend(@PathVariable Long userId,
                                                                 @RequestParam(defaultValue = "rule") String type,
                                                                 @RequestParam(defaultValue = "10") Integer limit) {
        return CommonResult.success(recommendRepository.userRecommend(userId, type, limit));
    }

    @GetMapping("/similar/{productId}")
    public CommonResult<List<Map<String, Object>>> similar(@PathVariable Long productId,
                                                           @RequestParam(defaultValue = "10") Integer limit) {
        return CommonResult.success(recommendRepository.similar(productId, limit));
    }

    @GetMapping("/evaluate")
    public CommonResult<List<Map<String, Object>>> evaluation() {
        return CommonResult.success(recommendRepository.evaluation());
    }
}
