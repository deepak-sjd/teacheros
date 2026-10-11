package com.teacheros.modules.attendance;

public enum AttendanceStatus {
    PRESENT,
    ABSENT,
    LATE,
    /**
     * Absent with a valid reason. Neither attended nor held against the
     * student: excluded from the attendance-percentage denominator (see
     * AttendanceRepository), so it never lowers the percentage.
     */
    EXCUSED
}
