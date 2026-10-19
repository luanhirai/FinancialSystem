package com.luan.FinancialSystem.repository;

import com.luan.FinancialSystem.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    @Query("select u from User u where u.client_id is not null and u.olist_access_token is not null")
    java.util.List<User> findOlistConnectedUsers();
    Optional<User> findByEmail(String email);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where lower(u.email) = lower(:email)")
    Optional<User> findByEmailForPasswordReset(@Param("email") String email);

    @Query("select u from User u where u.client_id = :clientId")
    Optional<User> findByClientId(@Param("clientId") String clientId);
}
