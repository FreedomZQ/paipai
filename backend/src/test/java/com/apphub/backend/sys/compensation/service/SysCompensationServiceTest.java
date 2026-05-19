package com.apphub.backend.sys.compensation.service;

import com.apphub.backend.apps.reading.compat.service.ReadingCloudUsageService;
import com.apphub.backend.common.util.Sha256HashService;
import com.apphub.backend.sys.app.model.AppDefinition;
import com.apphub.backend.sys.app.service.AppDefinitionService;
import com.apphub.backend.sys.compensation.entity.SysCompensationCodeEntity;
import com.apphub.backend.sys.compensation.mapper.SysCompensationCodeMapper;
import com.apphub.backend.sys.compensation.mapper.SysCompensationRedemptionMapper;
import com.apphub.backend.sys.compensation.model.CompensationCodeCreateRequest;
import com.apphub.backend.sys.compensation.model.CompensationCodeView;
import com.apphub.backend.sys.compensation.model.CompensationRedeemResultView;
import com.apphub.backend.sys.entitlement.entity.SysMembershipPlanEntity;
import com.apphub.backend.sys.entitlement.mapper.SysUserEntitlementGrantMapper;
import com.apphub.backend.sys.entitlement.service.SysEntitlementCenterService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.session.Configuration;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SysCompensationServiceTest {
    @Mock
    private SysCompensationCodeMapper codeMapper;
    @Mock
    private SysCompensationRedemptionMapper redemptionMapper;
    @Mock
    private SysUserEntitlementGrantMapper userEntitlementGrantMapper;
    @Mock
    private SysEntitlementCenterService entitlementCenterService;
    @Mock
    private ReadingCloudUsageService readingCloudUsageService;
    @Mock
    private AppDefinitionService appDefinitionService;

    private SysCompensationService service;

    @BeforeEach
    void setUp() {
        initMybatisPlusMetadata();
        service = new SysCompensationService(
            codeMapper,
            redemptionMapper,
            userEntitlementGrantMapper,
            entitlementCenterService,
            readingCloudUsageService,
            appDefinitionService,
            new ObjectMapper(),
            new Sha256HashService()
        );
    }

    private void initMybatisPlusMetadata() {
        Configuration configuration = new Configuration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, SysCompensationCodeEntity.class);
        TableInfoHelper.initTableInfo(assistant, com.apphub.backend.sys.compensation.entity.SysCompensationRedemptionEntity.class);
        TableInfoHelper.initTableInfo(assistant, com.apphub.backend.sys.entitlement.entity.SysUserEntitlementGrantEntity.class);
    }

    @Test
    void createCodeShouldGeneratePlanCodeWhenMissing() {
        mockAppDefinition();
        SysMembershipPlanEntity plan = new SysMembershipPlanEntity();
        plan.setAppCode("paipai_readingcompanion");
        plan.setPlanCode("standard_single_child");
        plan.setDisplayName("标准版");
        plan.setEntitlementCode("family_access");
        when(entitlementCenterService.listPlans("paipai_readingcompanion")).thenReturn(List.of(plan));
        when(codeMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        CompensationCodeView view = service.createCode(
            "paipai_readingcompanion",
            998L,
            new CompensationCodeCreateRequest(
                null,
                "plan",
                "standard_single_child",
                null,
                1,
                30,
                null,
                null,
                "single_use",
                1,
                null,
                "活动补偿"
            )
        );

        ArgumentCaptor<SysCompensationCodeEntity> captor = ArgumentCaptor.forClass(SysCompensationCodeEntity.class);
        verify(codeMapper).insert(captor.capture());
        assertThat(captor.getValue().getCompensationCode()).matches("^PP-(?:[A-Z2-9]{5}-){2}[A-Z2-9]{5}$");
        assertThat(view.planCode()).isEqualTo("standard_single_child");
        assertThat(view.benefitType()).isEqualTo("plan");
    }

    @Test
    void redeemPlanCodeShouldCreateGrantAndReturnSummary() {
        mockAppDefinition();
        SysMembershipPlanEntity plan = new SysMembershipPlanEntity();
        plan.setAppCode("paipai_readingcompanion");
        plan.setPlanCode("standard_single_child");
        plan.setDisplayName("标准版");
        plan.setEntitlementCode("family_access");
        when(entitlementCenterService.listPlans("paipai_readingcompanion")).thenReturn(List.of(plan));

        SysCompensationCodeEntity entity = new SysCompensationCodeEntity();
        entity.setId(11L);
        entity.setAppCode("paipai_readingcompanion");
        entity.setCompensationCode("PP-ABCDE-FGHJK-MNPQR");
        entity.setCodeHash("hash");
        entity.setBenefitType("plan");
        entity.setPlanCode("standard_single_child");
        entity.setEntitlementCode("family_access");
        entity.setGrantCount(1);
        entity.setGrantValidDays(30);
        entity.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusDays(1));
        entity.setMaxUses(1);
        entity.setUsedCount(0);
        entity.setStatus(SysCompensationService.STATUS_UNUSED);
        entity.setMetadataJson("{}");
        when(codeMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(entity);
        when(redemptionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        doReturn(1).when(codeMapper).update(isNull(), any(LambdaUpdateWrapper.class));

        CompensationRedeemResultView result = service.redeem(
            "paipai_readingcompanion",
            101L,
            "ios-test-device",
            "PP-ABCDE-FGHJK-MNPQR"
        );

        ArgumentCaptor<com.apphub.backend.sys.entitlement.entity.SysUserEntitlementGrantEntity> grantCaptor =
            ArgumentCaptor.forClass(com.apphub.backend.sys.entitlement.entity.SysUserEntitlementGrantEntity.class);
        ArgumentCaptor<com.apphub.backend.sys.compensation.entity.SysCompensationRedemptionEntity> recordCaptor =
            ArgumentCaptor.forClass(com.apphub.backend.sys.compensation.entity.SysCompensationRedemptionEntity.class);
        verify(userEntitlementGrantMapper).insert(grantCaptor.capture());
        verify(redemptionMapper).insert(recordCaptor.capture());
        assertThat(result.compensationCode()).isEqualTo("PP-ABCDE-FGHJK-MNPQR");
        assertThat(result.benefitType()).isEqualTo("plan");
        assertThat(result.benefitSummary()).contains("标准版");
    }

    @Test
    void redeemExpiredCodeShouldFail() {
        mockAppDefinition();
        SysCompensationCodeEntity entity = new SysCompensationCodeEntity();
        entity.setId(11L);
        entity.setAppCode("paipai_readingcompanion");
        entity.setCompensationCode("PP-ABCDE-FGHJK-MNPQR");
        entity.setBenefitType("plan");
        entity.setPlanCode("standard_single_child");
        entity.setGrantCount(1);
        entity.setGrantValidDays(30);
        entity.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1));
        entity.setMaxUses(1);
        entity.setUsedCount(0);
        entity.setStatus(SysCompensationService.STATUS_UNUSED);
        when(codeMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(entity);

        assertThatThrownBy(() -> service.redeem("paipai_readingcompanion", 101L, "ios-test-device", "PP-ABCDE-FGHJK-MNPQR"))
            .hasMessageContaining("已过期");
    }

    @Test
    void redeemInvalidFormatShouldFailFast() {
        mockAppDefinition();

        assertThatThrownBy(() -> service.redeem("paipai_readingcompanion", 101L, "ios-test-device", "bad-code"))
            .hasMessageContaining("格式不正确");
    }

    @Test
    void redeemUsageCreditShouldGrantCloudQuota() {
        mockAppDefinition();
        SysCompensationCodeEntity entity = new SysCompensationCodeEntity();
        entity.setId(21L);
        entity.setAppCode("paipai_readingcompanion");
        entity.setCompensationCode("PP-ABCDE-FGHJK-MNPQR");
        entity.setBenefitType(SysCompensationService.BENEFIT_USAGE_CREDIT);
        entity.setServiceType(ReadingCloudUsageService.CLOUD_TTS);
        entity.setGrantCount(3);
        entity.setGrantValidDays(14);
        entity.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusDays(1));
        entity.setMaxUses(1);
        entity.setUsedCount(0);
        entity.setStatus(SysCompensationService.STATUS_UNUSED);
        entity.setMetadataJson("{}");
        when(codeMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(entity);
        when(redemptionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        doReturn(1).when(codeMapper).update(isNull(), any(LambdaUpdateWrapper.class));

        CompensationRedeemResultView result = service.redeem(
            "paipai_readingcompanion",
            101L,
            "ios-test-device",
            "PP-ABCDE-FGHJK-MNPQR"
        );

        verify(readingCloudUsageService).grantPurchaseUntil(
            101L,
            ReadingCloudUsageService.CLOUD_TTS,
            "PP-ABCDE-FGHJK-MNPQR",
            3,
            result.validUntil() == null ? null : OffsetDateTime.parse(result.validUntil()),
            "PP-ABCDE-FGHJK-MNPQR"
        );
        assertThat(result.benefitType()).isEqualTo(SysCompensationService.BENEFIT_USAGE_CREDIT);
        assertThat(result.serviceType()).isEqualTo(ReadingCloudUsageService.CLOUD_TTS);
    }

    @Test
    void voidCodeShouldRejectUsedCode() {
        mockAppDefinition();
        SysCompensationCodeEntity entity = new SysCompensationCodeEntity();
        entity.setId(11L);
        entity.setAppCode("paipai_readingcompanion");
        entity.setCompensationCode("PP-ABCDE-FGHJK-MNPQR");
        entity.setStatus(SysCompensationService.STATUS_USED);
        when(codeMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(entity);

        assertThatThrownBy(() -> service.voidCode("paipai_readingcompanion", "PP-ABCDE-FGHJK-MNPQR", "结束"))
            .hasMessageContaining("不能作废");
    }

    private void mockAppDefinition() {
        when(appDefinitionService.get("paipai_readingcompanion")).thenReturn(Optional.of(
            new AppDefinition(
                "paipai_readingcompanion",
                "拍拍伴读",
                "/api/v1",
                "reading_",
                new AppDefinition.Support(true, true, true),
                Map.of()
            )
        ));
    }
}
