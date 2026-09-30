package com.teacheros.modules.assessment;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "test_answers")
@Getter
@Setter
@NoArgsConstructor
public class TestAnswer {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "attempt_id", nullable = false)
    private UUID attemptId;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    // Null if the student left the question blank.
    @Column(name = "selected_option")
    private String selectedOption;

    @Column(name = "is_correct")
    private Boolean isCorrect;

    @Column(name = "marks_awarded", precision = 6, scale = 2)
    private BigDecimal marksAwarded;

    public TestAnswer(UUID tenantId, UUID attemptId, UUID questionId, String selectedOption,
            Boolean isCorrect, BigDecimal marksAwarded) {
        this.tenantId = tenantId;
        this.attemptId = attemptId;
        this.questionId = questionId;
        this.selectedOption = selectedOption;
        this.isCorrect = isCorrect;
        this.marksAwarded = marksAwarded;
    }
}
