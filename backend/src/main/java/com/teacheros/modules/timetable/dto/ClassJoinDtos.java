package com.teacheros.modules.timetable.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public class ClassJoinDtos {

    /**
     * recorded=false is not an error: the click was fine but didn't count
     * as a join (the slot isn't scheduled for today). The student can
     * still open the meeting link either way.
     */
    public record JoinResponse(
            boolean recorded,
            Instant joinedAt) {
    }

    public record JoinedStudent(
            UUID studentId,
            String studentName,
            Instant joinedAt) {
    }

    public record SessionJoinsResponse(
            UUID slotId,
            UUID batchId,
            LocalDate sessionDate,
            String subject,
            LocalTime startTime,
            LocalTime endTime,
            int rosterSize,
            List<JoinedStudent> joined) {
    }
}
