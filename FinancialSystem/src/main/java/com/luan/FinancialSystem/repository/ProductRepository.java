package com.luan.FinancialSystem.repository;
import com.luan.FinancialSystem.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product,Long> {
    @Query("SELECT p FROM Product p JOIN p.ecommerce e JOIN e.user u WHERE u.id = :userId")
    List<Product> findProductsByUser(@Param("userId") Long userId);

}
