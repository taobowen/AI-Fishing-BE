package com.aifishing.webquota.repo;

import com.aifishing.webquota.domain.WebPlanQuotaLedger;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WebPlanQuotaLedgerRepository extends JpaRepository<WebPlanQuotaLedger, UUID> {
}
