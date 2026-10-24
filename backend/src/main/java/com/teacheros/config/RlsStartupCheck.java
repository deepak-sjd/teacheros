package com.teacheros.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.security.rls.enabled", havingValue = "true", matchIfMissing = true)
public class RlsStartupCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RlsStartupCheck.class);

    private final JdbcTemplate jdbc;
    private final Environment env;

    public RlsStartupCheck(JdbcTemplate jdbc, Environment env) {
        this.jdbc = jdbc;
        this.env = env;
    }

    @Override
    public void run(ApplicationArguments args) {
        Boolean bypasses = jdbc.queryForObject(
                "SELECT rolsuper OR rolbypassrls FROM pg_roles WHERE rolname = current_user", Boolean.class);
        if (Boolean.TRUE.equals(bypasses)) {
            String msg = "The database user is a superuser or has BYPASSRLS, so tenant row-level security is NOT "
                    + "enforced. Connect as a plain role (DB_USERNAME=teacheros_app) and run Flyway as the owner (FLYWAY_USER).";
            if (env.acceptsProfiles(Profiles.of("prod"))) {
                throw new IllegalStateException(msg);
            }
            log.warn(msg);
        }
    }
}
