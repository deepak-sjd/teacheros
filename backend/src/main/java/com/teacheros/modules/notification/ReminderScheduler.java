package com.teacheros.modules.notification;

import com.teacheros.modules.notification.dto.NotificationDtos.GenerateResult;
import com.teacheros.modules.tenant.Tenant;
import com.teacheros.modules.tenant.TenantRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.function.Function;

/**
 * Runs fee reminders and low-attendance alerts for every active tenant on a
 * schedule. @SchedulerLock guarantees one instance runs each job per tick;
 * lockAtLeastFor keeps a second instance with a slightly different tick from
 * re-running it right after. Each tenant runs in its own transaction (see
 * NotificationService), and a failure for one tenant is logged and skipped.
 * Disable with REMINDERS_ENABLED=false.
 */
@Component
@ConditionalOnProperty(name = "app.reminders.enabled", havingValue = "true", matchIfMissing = true)
public class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

    private final TenantRepository tenantRepository;
    private final NotificationService notificationService;
    private final ZoneId zone;
    private final int daysBeforeDue;
    private final double lowAttendanceThreshold;

    public ReminderScheduler(
            TenantRepository tenantRepository,
            NotificationService notificationService,
            @Value("${app.timezone:Asia/Kolkata}") String timezone,
            @Value("${app.reminders.days-before-due:2}") int daysBeforeDue,
            @Value("${app.reminders.low-attendance-threshold:75}") double lowAttendanceThreshold) {
        this.tenantRepository = tenantRepository;
        this.notificationService = notificationService;
        this.zone = ZoneId.of(timezone);
        this.daysBeforeDue = daysBeforeDue;
        this.lowAttendanceThreshold = lowAttendanceThreshold;
    }

    @Scheduled(cron = "${app.reminders.fee-cron:0 0 9 * * *}", zone = "${app.timezone:Asia/Kolkata}")
    @SchedulerLock(name = "reminders-fee", lockAtMostFor = "PT30M", lockAtLeastFor = "PT5M")
    public void runFeeReminders() {
        LocalDate today = LocalDate.now(zone);
        forEachTenant("fee reminders",
                tenantId -> notificationService.generateScheduledFeeRemindersForTenant(tenantId, today, daysBeforeDue));
    }

    @Scheduled(cron = "${app.reminders.attendance-cron:0 0 18 * * *}", zone = "${app.timezone:Asia/Kolkata}")
    @SchedulerLock(name = "reminders-attendance", lockAtMostFor = "PT30M", lockAtLeastFor = "PT5M")
    public void runLowAttendanceAlerts() {
        forEachTenant("low-attendance alerts",
                tenantId -> notificationService.generateLowAttendanceAlertsForTenant(tenantId, lowAttendanceThreshold));
    }

    private void forEachTenant(String jobName, Function<UUID, GenerateResult> action) {
        int tenants = 0;
        int failures = 0;
        int alerts = 0;
        for (Tenant tenant : tenantRepository.findAllByActiveTrue()) {
            tenants++;
            try {
                alerts += action.apply(tenant.getId()).created();
            } catch (Exception e) {
                failures++;
                log.error("Scheduled {} failed for tenant {}: {}", jobName, tenant.getId(), e.getMessage(), e);
            }
        }
        log.info("Scheduled {} finished: tenants={} alertsCreated={} tenantFailures={}",
                jobName, tenants, alerts, failures);
    }
}
