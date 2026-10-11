package com.teacheros.modules.feestructure;

import com.teacheros.modules.tenant.Tenant;
import com.teacheros.modules.tenant.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Nightly late-fee run, 02:30 IST.
 *
 * TODO(Dev 5): add Dev 5's ShedLock annotation here (lock name
 * "lateFeeRun") once their setup is merged, so only one instance runs it when
 * the backend is scaled out. Until then the DB unique index
 * uq_fees_late_fee_parent still prevents double-charging; a concurrent run
 * just fails harmlessly for the tenants it collides on.
 *
 * @EnableScheduling is harmless if Dev 5 already enabled it.
 */
@Component
@EnableScheduling
public class LateFeeScheduler {

    private static final Logger log = LoggerFactory.getLogger(LateFeeScheduler.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final TenantRepository tenantRepository;
    private final LateFeeService lateFeeService;

    public LateFeeScheduler(TenantRepository tenantRepository, LateFeeService lateFeeService) {
        this.tenantRepository = tenantRepository;
        this.lateFeeService = lateFeeService;
    }

    @Scheduled(cron = "0 30 2 * * *", zone = "Asia/Kolkata")
    public void run() {
        LocalDate today = LocalDate.now(IST);
        for (Tenant tenant : tenantRepository.findAll()) {
            if (!tenant.isActive()) {
                continue;
            }
            try {
                int created = lateFeeService.applyForTenant(tenant.getId(), today, null);
                if (created > 0) {
                    log.info("Late fees: tenant={} created={}", tenant.getId(), created);
                }
            } catch (Exception e) {
                // One tenant failing must not stop the others.
                log.error("Late fee run failed for tenant {}: {}", tenant.getId(), e.getMessage());
            }
        }
    }
}
