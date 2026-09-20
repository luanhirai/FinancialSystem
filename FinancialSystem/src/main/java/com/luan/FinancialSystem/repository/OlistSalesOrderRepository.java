package com.luan.FinancialSystem.repository;

import com.luan.FinancialSystem.entity.OlistSalesOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OlistSalesOrderRepository extends JpaRepository<OlistSalesOrder, Long> {
    Optional<OlistSalesOrder> findByUserIdAndOlistOrderId(Long userId, Long olistOrderId);
    List<OlistSalesOrder> findByUserIdOrderByOrderDateDescNumberDesc(Long userId);
}
