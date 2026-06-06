package com.luan.FinancialSystem.repository;

import com.luan.FinancialSystem.entity.PolicySetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PolicySettingRepository extends JpaRepository<PolicySetting,Long> {

    List<PolicySetting> findByUserId(Long userId);
}
