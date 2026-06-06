package com.luan.FinancialSystem.repository;

import com.luan.FinancialSystem.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    @Query("select u from User u where u.client_id = :clientId")
    Optional<User> findByClientId(@Param("clientId") String clientId);
}
