package com.luan.FinancialSystem.repository;
import com.luan.FinancialSystem.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product,Long> {
    @Query("select distinct trim(p.id_olist) from Product p join p.ecommerce e where e.user.id = :userId and p.id_olist is not null")
    List<String> findTinyIdsByUser(@Param("userId") Long userId);

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Product p set p.quantity = :quantity where trim(p.id_olist) = :tinyId and p.ecommerce.id in (select e.id from Ecommerce e where e.user.id = :userId)")
    int updateStockByTinyIdAndUserId(@Param("tinyId") String tinyId, @Param("userId") Long userId, @Param("quantity") Integer quantity);

    Optional<Product> findByIdAndEcommerceUserId(Long id, Long userId);
    boolean existsByEcommerceId(Long ecommerceId);
    @Query("SELECT p FROM Product p JOIN p.ecommerce e JOIN e.user u WHERE u.id = :userId")
    List<Product> findProductsByUser(@Param("userId") Long userId);

    @Query("SELECT p FROM Product p WHERE p.id_olist = :idOlist")
    Optional<Product> findByIdOlist(@Param("idOlist") String idOlist);

    @Query("SELECT p FROM Product p JOIN p.ecommerce e JOIN e.user u WHERE p.id_olist = :idOlist AND u.id = :userId")
    Optional<Product> findByIdOlistAndUserId(@Param("idOlist") String idOlist, @Param("userId") Long userId);
}
