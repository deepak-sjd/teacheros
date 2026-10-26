package com.teacheros.modules.lead;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.demo.DemoBookingRepository;
import com.teacheros.modules.demo.DemoBookingStatus;
import com.teacheros.modules.demo.dto.FunnelDtos.FunnelResponse;
import com.teacheros.modules.demo.dto.FunnelDtos.SourceRow;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * THE funnel numbers. Dev 10's owner dashboard should call funnel(from, to)
 * rather than
 * recomputing them, so the dashboard and this page can never disagree. Stage
 * counts are
 * by each lead's CURRENT status, over leads created in the window - with no
 * window they
 * equal the per-status counts on the leads list.
 */
@Service
public class LeadFunnelService {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Kolkata");
    private static final Instant FAR_FUTURE = Instant.parse("2999-12-31T00:00:00Z");

    private final LeadRepository leadRepository;
    private final DemoBookingRepository demoBookingRepository;
    private final CurrentTenantProvider currentTenantProvider;

    public LeadFunnelService(LeadRepository leadRepository, DemoBookingRepository demoBookingRepository,
            CurrentTenantProvider currentTenantProvider) {
        this.leadRepository = leadRepository;
        this.demoBookingRepository = demoBookingRepository;
        this.currentTenantProvider = currentTenantProvider;
    }

    public FunnelResponse funnel(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RANGE", "\"from\" must not be after \"to\"");
        }
        var tenantId = currentTenantProvider.getCurrentTenantId();
        Instant start = from == null ? Instant.EPOCH : from.atStartOfDay(APP_ZONE).toInstant();
        Instant end = to == null ? FAR_FUTURE : to.plusDays(1).atStartOfDay(APP_ZONE).toInstant();

        Map<LeadStatus, Long> byStage = new EnumMap<>(LeadStatus.class);
        for (LeadStatus s : LeadStatus.values())
            byStage.put(s, 0L);
        leadRepository.countByStatusBetween(tenantId, start, end)
                .forEach(r -> byStage.put(r.getStatus(), r.getTotal()));
        long total = byStage.values().stream().mapToLong(Long::longValue).sum();

        Map<LeadSource, long[]> sources = new EnumMap<>(LeadSource.class); // [total, enrolled]
        leadRepository.countBySourceAndStatusBetween(tenantId, start, end).forEach(r -> {
            long[] row = sources.computeIfAbsent(r.getSource(), k -> new long[2]);
            row[0] += r.getTotal();
            if (r.getStatus() == LeadStatus.ENROLLED)
                row[1] += r.getTotal();
        });
        List<SourceRow> bySource = sources.entrySet().stream()
                .map(e -> new SourceRow(e.getKey(), e.getValue()[0], e.getValue()[1],
                        rate(e.getValue()[1], e.getValue()[0])))
                .sorted(Comparator.comparingLong(SourceRow::total).reversed()).toList();

        long booked = demoBookingRepository.countDistinctLeads(tenantId,
                Set.of(DemoBookingStatus.BOOKED, DemoBookingStatus.SHOWED, DemoBookingStatus.NO_SHOW), start, end);
        long attended = demoBookingRepository.countDistinctLeads(tenantId, Set.of(DemoBookingStatus.SHOWED), start,
                end);

        return new FunnelResponse(from, to, total, byStage, rate(byStage.get(LeadStatus.ENROLLED), total), booked,
                attended, bySource);
    }

    public String exportCsv(LocalDate from, LocalDate to) {
        FunnelResponse f = funnel(from, to);
        StringBuilder sb = new StringBuilder("Stage,Leads\n");
        f.byStage().forEach((stage, n) -> sb.append(stage).append(',').append(n).append('\n'));
        sb.append("Total,").append(f.totalLeads()).append('\n')
                .append("Conversion rate %,").append(f.conversionRate()).append('\n')
                .append("Demos booked,").append(f.demosBooked()).append('\n')
                .append("Demos attended,").append(f.demosAttended()).append("\n\n")
                .append("Source,Leads,Enrolled,Conversion %\n");
        f.bySource().forEach(r -> sb.append(r.source()).append(',').append(r.total()).append(',')
                .append(r.enrolled()).append(',').append(r.conversionRate()).append('\n'));
        return sb.toString();
    }

    private double rate(long part, long whole) {
        return whole == 0 ? 0.0 : Math.round(part * 1000.0 / whole) / 10.0;
    }
}
