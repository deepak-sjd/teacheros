package com.teacheros.modules.attendance;

/**
 * Pure function, deliberately separated from AttendanceService so the core
 * business rule ("what counts toward attendance, how is the percentage
 * rounded") can be unit tested in milliseconds without a database.
 */
public final class AttendancePercentageCalculator {

    private AttendancePercentageCalculator() {
    }

    /**
     * @param recordedSessions total non-cancelled, non-EXCUSED sessions with an
     *                         attendance record (excused sessions are left out
     *                         by the repository queries, so they never lower
     *                         the percentage)
     * @param attendedSessions of those, how many were PRESENT or LATE
     * @return percentage rounded to 1 decimal place; 0.0 if no sessions recorded
     *         (never divides by zero, never returns NaN)
     */
    public static double calculate(long recordedSessions, long attendedSessions) {
        if (recordedSessions <= 0) {
            return 0.0;
        }
        if (attendedSessions < 0 || attendedSessions > recordedSessions) {
            throw new IllegalArgumentException(
                    "attendedSessions must be between 0 and recordedSessions");
        }
        double raw = (attendedSessions * 100.0) / recordedSessions;
        return Math.round(raw * 10.0) / 10.0;
    }
}
