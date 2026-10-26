package com.teacheros.modules.lead;

import com.teacheros.modules.notification.SmsSender;
import com.teacheros.modules.user.Role;
import com.teacheros.modules.user.User;
import com.teacheros.modules.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeadFollowUpReminderJobTest {

    @Mock
    LeadRepository leadRepository;
    @Mock
    UserRepository userRepository;
    @Mock
    SmsSender smsSender;

    private Lead lead(UUID assignee) {
        Lead l = new Lead(UUID.randomUUID(), "Asha", "+919876543210", null, null, LeadSource.WEBSITE);
        l.setId(UUID.randomUUID());
        l.setAssignedTo(assignee);
        return l;
    }

    @Test
    void twoClaimedLeadsForOneCounsellor_giveOneSms() {
        LeadFollowUpReminderJob job = new LeadFollowUpReminderJob(leadRepository, userRepository, smsSender);
        UUID counsellor = UUID.randomUUID();
        Lead one = lead(counsellor);
        Lead two = lead(counsellor);
        Lead alreadyReminded = lead(counsellor);
        User user = new User(UUID.randomUUID(), "+919000000009", "Priya", Role.TEACHER);

        LocalDate today = LocalDate.of(2026, 10, 12);
        when(leadRepository.findByNextFollowUpLessThanEqualAndAssignedToIsNotNullAndStatusIn(today,
                List.of(LeadStatus.NEW, LeadStatus.CONTACTED, LeadStatus.DEMO)))
                .thenReturn(List.of(one, two, alreadyReminded));
        when(leadRepository.claimFollowUpReminder(one.getId())).thenReturn(1);
        when(leadRepository.claimFollowUpReminder(two.getId())).thenReturn(1);
        when(leadRepository.claimFollowUpReminder(alreadyReminded.getId())).thenReturn(0);
        when(userRepository.findById(counsellor)).thenReturn(Optional.of(user));

        assertEquals(1, job.sendReminders(today));
        verify(smsSender).send(org.mockito.ArgumentMatchers.eq("+919000000009"), contains("2 lead follow-ups"));
    }

    @Test
    void nothingClaimed_sendsNothing() {
        LeadFollowUpReminderJob job = new LeadFollowUpReminderJob(leadRepository, userRepository, smsSender);
        Lead l = lead(UUID.randomUUID());
        when(leadRepository.findByNextFollowUpLessThanEqualAndAssignedToIsNotNullAndStatusIn(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(List.of(l));
        when(leadRepository.claimFollowUpReminder(l.getId())).thenReturn(0);

        assertEquals(0, job.sendReminders(LocalDate.of(2026, 10, 12)));
        verify(smsSender, never()).send(anyString(), anyString());
    }
}
