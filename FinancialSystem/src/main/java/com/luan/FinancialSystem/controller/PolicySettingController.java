package com.luan.FinancialSystem.controller;

import com.luan.FinancialSystem.entity.PolicySetting;
import com.luan.FinancialSystem.service.PolicySettingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/policy-settings")
public class PolicySettingController {

    private final PolicySettingService policySettingService;

    public PolicySettingController(PolicySettingService policySettingService) {
        this.policySettingService = policySettingService;
    }

    @GetMapping
    public PolicySettingResponse getCurrentSetting() {
        return toResponse(policySettingService.getCurrentSetting());
    }

    @PostMapping
    public ResponseEntity<?> saveSetting(@RequestBody PolicySettingRequest request) {
        try {
            return ResponseEntity.ok(toResponse(policySettingService.saveRate(request.rate())));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(exception.getMessage());
        }
    }

    private PolicySettingResponse toResponse(PolicySetting setting) {
        Long userId = setting.getUser() != null ? setting.getUser().getId() : null;
        return new PolicySettingResponse(setting.getId(), setting.getRate(), userId);
    }

    public record PolicySettingRequest(Float rate) {
    }

    public record PolicySettingResponse(Long id, Float rate, Long userId) {
    }
}
