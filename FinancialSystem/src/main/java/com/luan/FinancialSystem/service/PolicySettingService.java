package com.luan.FinancialSystem.service;

import com.luan.FinancialSystem.entity.PolicySetting;
import com.luan.FinancialSystem.entity.User;
import com.luan.FinancialSystem.repository.PolicySettingRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

@Service
public class PolicySettingService {

    private final PolicySettingRepository repository;
    private final AuthenticatedUserService authenticatedUserService;

    public PolicySettingService(PolicySettingRepository repository,
                                AuthenticatedUserService authenticatedUserService) {
        this.repository = repository;
        this.authenticatedUserService = authenticatedUserService;
    }

    public PolicySetting getCurrentSetting() {
        User user = authenticatedUserService.getLoggedUser();

        return repository.findByUserId(user.getId()).stream()
                .findFirst()
                .orElseGet(() -> {
                    PolicySetting setting = new PolicySetting();
                    setting.setRate(0F);
                    setting.setUser(user);
                    return setting;
                });
    }

    @Transactional
    public PolicySetting saveRate(Float rate) {
        if (rate == null) {
            throw new IllegalArgumentException("Informe o percentual de imposto.");
        }

        if (rate < 0) {
            throw new IllegalArgumentException("O percentual de imposto nao pode ser negativo.");
        }

        User user = authenticatedUserService.getLoggedUser();

        PolicySetting setting = repository.findByUserId(user.getId()).stream()
                .findFirst()
                .orElseGet(PolicySetting::new);

        setting.setRate(rate);
        setting.setUser(user);
        return repository.save(setting);
    }
}
