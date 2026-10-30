package com.luan.FinancialSystem.repository;

import com.luan.FinancialSystem.entity.OlistSalesOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OlistSalesOrderRepository extends JpaRepository<OlistSalesOrder, Long> {
    List<OlistSalesOrder> findByUserId(Long userId);
    boolean existsByUserIdAndOlistOrderId(Long userId, Long olistOrderId);
    Optional<OlistSalesOrder> findByUserIdAndOlistOrderId(Long userId, Long olistOrderId);
    List<OlistSalesOrder> findByUserIdAndOrderDateBetweenOrderByOrderDateDescNumberDesc(Long userId, java.time.LocalDate start, java.time.LocalDate end);
    List<OlistSalesOrder> findByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByOrderDateDescNumberDesc(Long userId, java.time.Instant start, java.time.Instant end);
}
