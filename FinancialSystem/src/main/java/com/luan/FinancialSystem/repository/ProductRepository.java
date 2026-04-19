package com.luan.FinancialSystem.repository;
import com.luan.FinancialSystem.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product,Long> {
    Optional<Product> findByName(String name);

    @Query("SELECT p FROM Product p WHERE p.ecommerce.id = :id")
    List<Product> findProductsByEcommerce(@Param("id") Long id);
}
