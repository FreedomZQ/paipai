package com.apphub.backend.apps.reading.compat.service;

import com.apphub.backend.apps.reading.domain.entity.ReadingWeeklyReportSnapshotEntity;
import com.apphub.backend.apps.reading.domain.mapper.ReadingWeeklyReportSnapshotMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReadingWeeklyReportSnapshotServiceTest {
    @Mock private ReadingWeeklyReportSnapshotMapper snapshotMapper;

    @Test
    void saveShouldPersistAggregatedSnapshotJsonWithoutPlainCardTextContract() {
        ReadingWeeklyReportSnapshotService service = new ReadingWeeklyReportSnapshotService(snapshotMapper, new ObjectMapper());
        ReadingCompatService.WeeklyParentReportView report = report();

        service.save(42L, "child-a", "child", LocalDate.parse("2026-04-13"), "standard_single_child", "standard", report);

        ArgumentCaptor<ReadingWeeklyReportSnapshotEntity> captor = ArgumentCaptor.forClass(ReadingWeeklyReportSnapshotEntity.class);
        verify(snapshotMapper).insert(captor.capture());
        ReadingWeeklyReportSnapshotEntity saved = captor.getValue();
        assertThat(saved.getAppCode()).isEqualTo("paipai_readingcompanion");
        assertThat(saved.getUserId()).isEqualTo(42L);
        assertThat(saved.getChildId()).isEqualTo("child-a");
        assertThat(saved.getScope()).isEqualTo("child");
        assertThat(saved.getPlanCode()).isEqualTo("standard_single_child");
        assertThat(saved.getReportPayloadJson()).contains("本报告仅用于家庭陪读参考");
        assertThat(saved.getReportPayloadJson()).doesNotContain("enc:v1:aesgcm");
    }

    @Test
    void loadShouldDecodeSnapshotPayload() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ReadingWeeklyReportSnapshotService service = new ReadingWeeklyReportSnapshotService(snapshotMapper, objectMapper);
        ReadingWeeklyReportSnapshotEntity entity = new ReadingWeeklyReportSnapshotEntity();
        entity.setReportPayloadJson(objectMapper.writeValueAsString(report()));
        given(snapshotMapper.selectActiveSnapshot(eq("paipai_readingcompanion"), eq(42L), eq("child-a"), eq("child"), any(), eq("standard_single_child")))
            .willReturn(entity);

        Optional<ReadingCompatService.WeeklyParentReportView> loaded = service.load(42L, "child-a", "child", LocalDate.parse("2026-04-13"), "standard_single_child");

        assertThat(loaded).isPresent();
        assertThat(loaded.get().childId()).isEqualTo("child-a");
        assertThat(loaded.get().modules()).extracting(ReadingCompatService.WeeklyReportModuleView::code).contains("basic_stats");
    }

    private ReadingCompatService.WeeklyParentReportView report() {
        ReadingCompatService.WeeklyStatsView stats = new ReadingCompatService.WeeklyStatsView(1, 2, 3, 4, 1, 0, 0, 0, 3, 1);
        return new ReadingCompatService.WeeklyParentReportView(
            "child|2026-04-13",
            "child",
            "2026-04-13",
            "2026-04-19",
            true,
            "child-a",
            "小宝",
            stats,
            "这一周已经留下陪读和复习记录，可作为家庭回顾参考。",
            "已经开始复习，继续保持。",
            "下周建议保持每天 1 句，优先处理到期复习卡。",
            List.of("每天只拍一句，降低孩子压力。"),
            "本报告仅用于家庭陪读参考，不用于学业评价、排名、诊断、医疗、心理或任何高风险判断。",
            List.of(),
            "2026-04-20T00:00:00Z",
            "standard_single_child",
            "standard",
            true,
            false,
            true,
            List.of(new ReadingCompatService.WeeklyReportModuleView("basic_stats", "本周基础回顾", "full", Map.of("weeklyReviewCount", 2)))
        );
    }
}
