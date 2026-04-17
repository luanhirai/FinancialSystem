package com.luan.FinancialSystem.repository;
import com.luan.FinancialSystem.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product,Long> {
}
