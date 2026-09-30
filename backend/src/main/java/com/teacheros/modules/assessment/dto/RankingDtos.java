package com.teacheros.modules.assessment.dto;

import java.util.List;
import java.util.UUID;

public class RankingDtos {

    /**
     * One student's position in their batch's ranking. rank is null for a
     * student with no graded marks yet - they exist in the roster but
     * can't be ranked against classmates on nothing, so they're listed
     * separately rather than all sharing a fake rank 1.
     */
    public record BatchRankingEntry(
            UUID studentId,
            String studentName,
            Integer rank,
            double averagePercentage,
            long gradedTestCount) {
    }

    /**
     * Staff-only view (see AssessmentController) - every student in a batch,
     * by name, same "seeing classmates' results is a staff privilege" rule
     * MarkResponse/listMarksForTest already follows.
     */
    public record BatchRankingResponse(
            UUID batchId,
            String batchName,
            long rankedStudentCount,
            List<BatchRankingEntry> entries) {
    }

    /**
     * The PARENT/STUDENT-safe view of the same computation: just this one
     * student's own position and how many peers they're ranked against,
     * never classmates' names or scores.
     */
    public record StudentRankResponse(
            UUID studentId,
            UUID batchId,
            Integer rank,
            long rankedStudentCount,
            double averagePercentage,
            long gradedTestCount) {
    }
}
