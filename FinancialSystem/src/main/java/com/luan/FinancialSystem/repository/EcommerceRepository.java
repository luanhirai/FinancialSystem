package com.luan.FinancialSystem.repository;
import com.luan.FinancialSystem.entity.Ecommerce;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EcommerceRepository extends JpaRepository<Ecommerce,Long> {

    List<Ecommerce> findByUserId(Long userId);
}
