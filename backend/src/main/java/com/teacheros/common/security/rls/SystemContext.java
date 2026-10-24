package com.teacheros.common.security.rls;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * The only way to read across tenants. Use for schedulers, unauthenticated
 * webhooks and the first-login phone lookup. Opens a NEW transaction (and so a
 * new connection) with RLS bypass on, so it works even when called from inside
 * an outer transaction. Keep the work inside as small as possible.
 */
@Component
public class SystemContext {

    private static final ThreadLocal<Boolean> ACTIVE = new ThreadLocal<>();
    private final TransactionTemplate tx;

    public SystemContext(PlatformTransactionManager transactionManager) {
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    static boolean isActive() {
        return Boolean.TRUE.equals(ACTIVE.get());
    }

    public <T> T call(Supplier<T> work) {
        Boolean previous = ACTIVE.get();
        ACTIVE.set(true);
        try {
            return tx.execute(status -> work.get());
        } finally {
            if (previous == null) {
                ACTIVE.remove();
            } else {
                ACTIVE.set(previous);
            }
        }
    }

    public void run(Runnable work) {
        call(() -> {
            work.run();
            return null;
        });
    }
}
