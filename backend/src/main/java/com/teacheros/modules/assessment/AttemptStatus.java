package com.teacheros.modules.assessment;

/**
 * NOT_STARTED is deliberately not a value here - it's represented by the
 * absence of a TestAttempt row, not a status on one. Only a row that
 * exists can be IN_PROGRESS or SUBMITTED.
 */
public enum AttemptStatus {
    IN_PROGRESS,
    SUBMITTED
}
