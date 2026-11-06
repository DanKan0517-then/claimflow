package com.claimflow.analytics;

import com.claimflow.analytics.AnalyticsSummary.AdjusterLoad;
import com.claimflow.analytics.AnalyticsSummary.DailyCount;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/** Dashboard numbers straight from Postgres aggregates, cached in Redis for 30s and shared by all replicas. */
@Service
public class AnalyticsService {

    @PersistenceContext
    private EntityManager em;

    @Cacheable("analytics")
    @Transactional(readOnly = true)
    public AnalyticsSummary summary() {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (Object[] r : rows("select status, count(*) from claims where status <> 'DRAFT' group by status order by status")) {
            byStatus.put((String) r[0], num(r[1]));
        }
        Map<String, Long> byType = new LinkedHashMap<>();
        for (Object[] r : rows("select type, count(*) from claims where status <> 'DRAFT' group by type order by type")) {
            byType.put((String) r[0], num(r[1]));
        }
        Map<String, Long> byRisk = new LinkedHashMap<>();
        byRisk.put("LOW", 0L);
        byRisk.put("MEDIUM", 0L);
        byRisk.put("HIGH", 0L);
        for (Object[] r : rows("""
                select case when fraud_score >= 70 then 'HIGH' when fraud_score >= 40 then 'MEDIUM' else 'LOW' end, count(*)
                from claims where fraud_score is not null group by 1""")) {
            byRisk.put((String) r[0], num(r[1]));
        }

        Object[] totals = rows("""
                select count(*),
                       count(*) filter (where status in ('SUBMITTED','AI_REVIEW','UNDER_REVIEW','INFO_REQUESTED','PENDING_APPROVAL')),
                       count(*) filter (where (decided_at is null and status not in ('APPROVED','REJECTED','PAID') and now() > sla_due_at)
                                           or (decided_at is not null and decided_at > sla_due_at)),
                       count(*) filter (where fraud_score >= 70),
                       coalesce(sum(claimed_amount), 0),
                       coalesce(sum(approved_amount) filter (where status in ('APPROVED','PAID')), 0),
                       count(*) filter (where status in ('APPROVED','PAID')),
                       count(*) filter (where status in ('APPROVED','PAID','REJECTED')),
                       coalesce(avg(extract(epoch from (decided_at - created_at)) / 3600.0) filter (where decided_at is not null), 0)
                from claims where status <> 'DRAFT'""").get(0);

        long decidedCount = num(totals[7]);
        double approvalRate = decidedCount == 0 ? 0 : (double) num(totals[6]) / decidedCount;

        // last 14 days, zero-filled so the chart has no gaps
        Map<String, long[]> days = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();
        for (int i = 13; i >= 0; i--) days.put(today.minusDays(i).toString(), new long[2]);
        for (Object[] r : rows("""
                select to_char(created_at at time zone 'UTC', 'YYYY-MM-DD'), count(*) from claims
                where status <> 'DRAFT' and created_at >= now() - interval '14 days' group by 1""")) {
            long[] v = days.get((String) r[0]);
            if (v != null) v[0] = num(r[1]);
        }
        for (Object[] r : rows("""
                select to_char(decided_at at time zone 'UTC', 'YYYY-MM-DD'), count(*) from claims
                where decided_at >= now() - interval '14 days' group by 1""")) {
            long[] v = days.get((String) r[0]);
            if (v != null) v[1] = num(r[1]);
        }
        List<DailyCount> daily = days.entrySet().stream()
                .map(e -> new DailyCount(e.getKey(), e.getValue()[0], e.getValue()[1])).toList();

        List<AdjusterLoad> adjusters = new ArrayList<>();
        for (Object[] r : rows("""
                select u.id, u.full_name,
                       count(c.id) filter (where c.status in ('UNDER_REVIEW','PENDING_APPROVAL','INFO_REQUESTED','AI_REVIEW')),
                       count(c.id) filter (where c.status in ('APPROVED','REJECTED','PAID'))
                from users u left join claims c on c.assigned_adjuster_id = u.id
                where u.role = 'ADJUSTER' and u.active
                group by u.id, u.full_name order by u.full_name""")) {
            adjusters.add(new AdjusterLoad(num(r[0]), (String) r[1], num(r[2]), num(r[3])));
        }

        return new AnalyticsSummary(num(totals[0]), num(totals[1]), num(totals[2]), num(totals[3]),
                dec(totals[4]), dec(totals[5]), round(approvalRate, 3), round(((Number) totals[8]).doubleValue(), 1),
                byStatus, byType, byRisk, daily, adjusters, Instant.now());
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> rows(String sql) {
        return em.createNativeQuery(sql).getResultList();
    }

    private static long num(Object o) {
        return o == null ? 0 : ((Number) o).longValue();
    }

    private static BigDecimal dec(Object o) {
        if (o == null) return BigDecimal.ZERO;
        return o instanceof BigDecimal b ? b : new BigDecimal(o.toString());
    }

    private static double round(double v, int places) {
        double f = Math.pow(10, places);
        return Math.round(v * f) / f;
    }
}
