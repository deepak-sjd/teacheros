package com.teacheros.modules.fee;

/**
 * STANDARD: a fee created the original way (flat amount, one due date).
 * INSTALMENT: generated from a fee structure's instalment plan.
 * LATE_FEE: a separate charge added for an overdue INSTALMENT.
 */
public enum FeeLineType {
    STANDARD,
    INSTALMENT,
    LATE_FEE
}
