package io.wulfcodes.messaging.chat.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables @Scheduled (membership heartbeat + ring refresh). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
