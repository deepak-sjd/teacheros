package com.teacheros.modules.fee;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Marks fees OVERDUE once their due date has passed. Idempotent (a plain
 * bulk UPDATE), so running on several instances at once is harmless.
 * Cron is overridable via app.fees.overdue-cron; default is 00:05 IST daily.
 */
@Component
public class FeeOverdueScheduler {

    private static final Logger log = LoggerFactory.getLogger(FeeOverdueScheduler.class);

    private final FeeRepository feeRepository;

    public FeeOverdueScheduler(FeeRepository feeRepository) {
        this.feeRepository = feeRepository;
    }

    @Scheduled(cron = "${app.fees.overdue-cron:0 5 0 * * *}", zone = "Asia/Kolkata")
    public void markOverdueFees() {
        int updated = feeRepository.sweepOverdueAll(FeeService.today());
        log.info("Overdue sweep marked {} fee(s) as OVERDUE", updated);
    }
}
