package com.teacheros.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables @Scheduled jobs (currently only FeeOverdueScheduler). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
