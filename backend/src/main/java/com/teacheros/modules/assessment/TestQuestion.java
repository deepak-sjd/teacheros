package com.teacheros.modules.assessment;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "test_questions")
@Getter
@Setter
@NoArgsConstructor
public class TestQuestion {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "test_id", nullable = false)
    private UUID testId;

    @Column(name = "question_text", nullable = false)
    private String questionText;

    @Column(name = "option_a", nullable = false)
    private String optionA;

    @Column(name = "option_b", nullable = false)
    private String optionB;

    @Column(name = "option_c", nullable = false)
    private String optionC;

    @Column(name = "option_d", nullable = false)
    private String optionD;

    // 'A' | 'B' | 'C' | 'D' - validated by the DTO's @Pattern, not an enum,
    // since it maps directly onto the option letter a student submits.
    @Column(name = "correct_option", nullable = false)
    private String correctOption;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal marks;

    @Column(nullable = false)
    private int position;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public TestQuestion(UUID tenantId, UUID testId, String questionText, String optionA, String optionB,
            String optionC, String optionD, String correctOption, BigDecimal marks, int position) {
        this.tenantId = tenantId;
        this.testId = testId;
        this.questionText = questionText;
        this.optionA = optionA;
        this.optionB = optionB;
        this.optionC = optionC;
        this.optionD = optionD;
        this.correctOption = correctOption;
        this.marks = marks;
        this.position = position;
    }

    public String optionFor(String letter) {
        return switch (letter) {
            case "A" -> optionA;
            case "B" -> optionB;
            case "C" -> optionC;
            case "D" -> optionD;
            default -> null;
        };
    }
}
