package com.teacheros.modules.lead;

import com.teacheros.modules.notification.SmsSender;
import com.teacheros.modules.user.User;
import com.teacheros.modules.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Daily SMS to each counsellor with due follow-ups. A system job, so its lead
 * query is
 * deliberately not tenant-scoped (each lead's assignee was validated against
 * its own tenant
 * when assigned). Each follow-up date is reminded once: claimFollowUpReminder
 * is an atomic
 * UPDATE, so this is also safe if two backend instances run it. When Dev 5's
 * automation
 * engine lands, replace the schedule with an automation rule but keep the
 * claim.
 */
@Component
@ConditionalOnProperty(name = "app.leads.follow-up-reminder.enabled", havingValue = "true", matchIfMissing = true)
public class LeadFollowUpReminderJob {

    private static final Logger log = LoggerFactory.getLogger(LeadFollowUpReminderJob.class);
    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Kolkata");
    private static final List<LeadStatus> OPEN = List.of(LeadStatus.NEW, LeadStatus.CONTACTED, LeadStatus.DEMO);

    private final LeadRepository leadRepository;
    private final UserRepository userRepository;
    private final SmsSender smsSender;

    public LeadFollowUpReminderJob(LeadRepository leadRepository, UserRepository userRepository, SmsSender smsSender) {
        this.leadRepository = leadRepository;
        this.userRepository = userRepository;
        this.smsSender = smsSender;
    }

    @Scheduled(cron = "${app.leads.follow-up-reminder.cron:0 0 9 * * *}", zone = "Asia/Kolkata")
    public void run() {
        int sent = sendReminders(LocalDate.now(APP_ZONE));
        if (sent > 0)
            log.info("Sent {} lead follow-up reminder(s)", sent);
    }

    /** @return how many counsellors were texted. */
    public int sendReminders(LocalDate today) {
        Map<UUID, Integer> claimedByAssignee = new LinkedHashMap<>();
        for (Lead lead : leadRepository.findByNextFollowUpLessThanEqualAndAssignedToIsNotNullAndStatusIn(today, OPEN)) {
            if (leadRepository.claimFollowUpReminder(lead.getId()) == 1) {
                claimedByAssignee.merge(lead.getAssignedTo(), 1, Integer::sum);
            }
        }

        int texted = 0;
        for (Map.Entry<UUID, Integer> e : claimedByAssignee.entrySet()) {
            User user = userRepository.findById(e.getKey()).filter(User::isActive).orElse(null);
            if (user == null || user.getPhoneNumber() == null || user.getPhoneNumber().isBlank())
                continue;
            try {
                smsSender.send(user.getPhoneNumber(),
                        "TeacherOS: you have %d lead follow-up%s due. Open Leads to see them."
                                .formatted(e.getValue(), e.getValue() == 1 ? "" : "s"));
                texted++;
            } catch (Exception ex) {
                log.error("Failed to send follow-up reminder to user {}: {}", e.getKey(), ex.getMessage());
            }
        }
        return texted;
    }
}
