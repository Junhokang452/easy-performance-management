package com.easyperformance.domain.kpi.service;

import com.easyperformance.domain.kpi.entity.KpiActual;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class KpiActualSelectorTest {
    @Test void currentLeafIsChosenInsteadOfSupersededRoot() {
        UUID rootId=UUID.randomUUID(); KpiActual root=row(rootId,null,LocalDate.of(2026,6,30),"70");
        KpiActual correction=row(UUID.randomUUID(),rootId,LocalDate.of(2026,6,30),"90");
        assertThat(KpiActualSelector.latestCurrentLeaf(List.of(root,correction),LocalDate.of(2026,6,30)).getActualValue())
            .isEqualByComparingTo("90");
    }

    @Test void futureDatedSuccessorDoesNotResurrectRootBeforeCutoff() {
        UUID rootId=UUID.randomUUID(); KpiActual root=row(rootId,null,LocalDate.of(2026,6,30),"70");
        KpiActual future=row(UUID.randomUUID(),rootId,LocalDate.of(2026,9,30),"90");
        assertThat(KpiActualSelector.latestCurrentLeaf(List.of(root,future),LocalDate.of(2026,6,30))).isNull();
    }

    private static KpiActual row(UUID id,UUID supersedes,LocalDate date,String value){KpiActual row=new KpiActual();row.setId(id);row.setSupersedesId(supersedes);row.setAsOfDate(date);row.setActualValue(new BigDecimal(value));return row;}
}
