package com.easyperformance.domain.kpi.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Shared server-side KPI achievement calculation. */
public final class KpiScorePolicy {
    public static final String VERSION = "KPI_ACHIEVEMENT_V1";
    public static final String FORMULA = "clamp(round(round(actualValue/effectiveTarget,6)*100,2),0,100)";
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private KpiScorePolicy() {}

    public static BigDecimal achievementRate(BigDecimal actualValue, BigDecimal effectiveTarget) {
        if (actualValue == null || effectiveTarget == null || effectiveTarget.compareTo(ZERO) == 0) return null;
        return actualValue.divide(effectiveTarget, 6, RoundingMode.HALF_UP);
    }

    public static BigDecimal autoScore(BigDecimal achievementRate) {
        if (achievementRate == null) return null;
        return achievementRate.multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP)
            .max(ZERO.setScale(2)).min(HUNDRED.setScale(2));
    }
}
