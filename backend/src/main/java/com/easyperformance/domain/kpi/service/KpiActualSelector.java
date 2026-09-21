package com.easyperformance.domain.kpi.service;

import com.easyperformance.domain.kpi.entity.KpiActual;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Selects a current append-only leaf before applying the business-date cutoff. */
public final class KpiActualSelector {
    private KpiActualSelector() {}

    public static KpiActual latestCurrentLeaf(List<KpiActual> history, LocalDate cutoff) {
        Set<java.util.UUID> superseded = new HashSet<>();
        history.stream().map(KpiActual::getSupersedesId).filter(java.util.Objects::nonNull).forEach(superseded::add);
        return history.stream()
            .filter(row -> !superseded.contains(row.getId()))
            .filter(row -> cutoff == null || !row.getAsOfDate().isAfter(cutoff))
            .max(Comparator.comparing(KpiActual::getAsOfDate)
                .thenComparing(KpiActual::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(KpiActual::getId))
            .orElse(null);
    }
}
