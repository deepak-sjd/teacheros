package com.teacheros.modules.studyplan.dto;

import java.time.Instant;
import java.util.List;

public class StudyPlanDtos {

    public record SubjectBreakdown(
            String subject,
            double averagePercentage,
            long gradedEntries,
            boolean weak) {
    }

    /**
     * subjects covers EVERY subject the student has a graded mark in, not
     * just the weak ones - the frontend filters for weak==true to render
     * the highlighted chips, but the full breakdown is there too in case
     * it's ever useful to show the complete picture. planText/generatedAt
     * are both null until the student (or their parent/a staff member)
     * has generated a plan at least once.
     */
    public record StudyPlanResponse(
            List<SubjectBreakdown> subjects,
            String planText,
            Instant generatedAt) {
    }
}
