package com.aifishing.webquota.repo;

import com.aifishing.webquota.domain.UserWebPlanEntitlement;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserWebPlanEntitlementRepository extends JpaRepository<UserWebPlanEntitlement, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from UserWebPlanEntitlement e where e.userId = :userId")
    Optional<UserWebPlanEntitlement> lockByUserId(@Param("userId") UUID userId);
}
