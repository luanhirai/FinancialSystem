package com.luan.FinancialSystem.repository;
import com.luan.FinancialSystem.entity.OlistAuthorization;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface OlistAuthorizationRepository extends JpaRepository<OlistAuthorization, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from OlistAuthorization a where a.stateHash = :hash")
    Optional<OlistAuthorization> findForUpdate(@Param("hash") String hash);
}
