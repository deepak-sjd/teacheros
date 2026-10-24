package com.teacheros.modules.fee;

import com.teacheros.common.security.rls.SystemContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FeeOverdueScheduler {

    private static final Logger log = LoggerFactory.getLogger(FeeOverdueScheduler.class);

    private final FeeRepository feeRepository;
    private final SystemContext systemContext;

    public FeeOverdueScheduler(FeeRepository feeRepository, SystemContext systemContext) {
        this.feeRepository = feeRepository;
        this.systemContext = systemContext;
    }

    @Scheduled(cron = "${app.fees.overdue-cron:0 5 0 * * *}", zone = "Asia/Kolkata")
    public void markOverdueFees() {
        int updated = systemContext.call(() -> feeRepository.sweepOverdueAll(FeeService.today()));
        log.info("Overdue sweep marked {} fee(s) as OVERDUE", updated);
    }
}
