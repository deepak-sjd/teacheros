package com.teacheros.modules.demo.dto;

import com.teacheros.modules.lead.LeadSource;
import com.teacheros.modules.lead.LeadStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class FunnelDtos {

    /** from/to are null for "all time". */
    public record FunnelResponse(
            LocalDate from,
            LocalDate to,
            long totalLeads,
            Map<LeadStatus, Long> byStage, // every stage present, zero-filled
            double conversionRate, // ENROLLED / totalLeads, percent, 1 decimal
            long demosBooked, // distinct leads with a non-cancelled booking
            long demosAttended, // distinct leads marked SHOWED
            List<SourceRow> bySource) {
    }

    public record SourceRow(LeadSource source, long total, long enrolled, double conversionRate) {
    }
}
