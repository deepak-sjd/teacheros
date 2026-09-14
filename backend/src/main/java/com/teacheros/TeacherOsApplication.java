package com.teacheros;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * TeacherOS backend entry point.
 *
 * Architecture: modular monolith. Packages under {@code com.teacheros.modules}
 * represent feature modules (auth, tenant, student, batch, attendance, fee).
 * Shared cross-cutting concerns (security, exception handling, logging,
 * tenant context) live under {@code com.teacheros.common}.
 *
 * Do not split this into microservices until real scale requires it -
 * see project architecture decision log.
 */
@SpringBootApplication
public class TeacherOsApplication {

    public static void main(String[] args) {
        SpringApplication.run(TeacherOsApplication.class, args);
    }
}
