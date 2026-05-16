# DB four-table dependency matrix

Generated from migration CREATE TABLE, `@TableName`, and Java literal SQL usage. Non-destructive analysis artifact.

## Summary

- common: 22 current tables
- paipai: 14 current tables
- saving: 2 current tables
- fitmystery: 10 current tables
- total current tables detected: 48

## Matrix

| current_table | current_area | target_four_table | entity_files | migration_origin | Java refs | startup risk if old table removed |
|---|---|---|---|---|---|---|
| `fit_account_deletion_request` | `fitmystery` | `app_fitmystery_record` | - | `V31__fitmystery_v1_core.sql` | `FitMysteryAccountMapper.java` | `HIGH` |
| `fit_activity_event` | `fitmystery` | `app_fitmystery_record` | - | `V31__fitmystery_v1_core.sql` | `FitMysteryAccountMapper.java`<br>`FitMysteryActivityMapper.java` | `HIGH` |
| `fit_blind_box_draw` | `fitmystery` | `app_fitmystery_record` | - | `V31__fitmystery_v1_core.sql` | `FitMysteryAccountMapper.java`<br>`FitMysteryBoxMapper.java` | `HIGH` |
| `fit_blind_box_item` | `fitmystery` | `app_fitmystery_record` | - | `V31__fitmystery_v1_core.sql` | `FitMysteryBoxMapper.java` | `HIGH` |
| `fit_blind_box_pool` | `fitmystery` | `app_fitmystery_record` | - | `V31__fitmystery_v1_core.sql` | - | `MEDIUM` |
| `fit_daily_score_snapshot` | `fitmystery` | `app_fitmystery_record` | - | `V31__fitmystery_v1_core.sql` | `FitMysteryAccountMapper.java`<br>`FitMysteryActivityMapper.java` | `HIGH` |
| `fit_draw_chance_ledger` | `fitmystery` | `app_fitmystery_record` | - | `V31__fitmystery_v1_core.sql` | `FitMysteryAccountMapper.java`<br>`FitMysteryActivityMapper.java`<br>`FitMysteryBoxMapper.java`<br>`FitMysteryPurchaseMapper.java` | `HIGH` |
| `fit_points_ledger` | `fitmystery` | `app_fitmystery_record` | - | `V31__fitmystery_v1_core.sql` | `FitMysteryAccountMapper.java`<br>`FitMysteryActivityMapper.java`<br>`FitMysteryBoxMapper.java` | `HIGH` |
| `fit_report_generation_ledger` | `fitmystery` | `app_fitmystery_record` | - | `V31__fitmystery_v1_core.sql` | `FitMysteryAccountMapper.java`<br>`FitMysteryReportMapper.java` | `HIGH` |
| `fit_user_collection` | `fitmystery` | `app_fitmystery_record` | - | `V31__fitmystery_v1_core.sql` | `FitMysteryAccountMapper.java`<br>`FitMysteryBoxMapper.java` | `HIGH` |
| `reading_announcement` | `paipai` | `app_paipai_record` | `ReadingAnnouncementEntity.java` | `V1__init.sql` | `ReadingAnnouncementEntity.java`<br>`ReadingAnnouncementMapper.java` | `HIGH` |
| `reading_child_profile` | `paipai` | `app_paipai_record` | `ReadingChildProfileEntity.java` | `V1__init.sql` | `ReadingChildProfileEntity.java`<br>`ReadingChildProfileMapper.java`<br>`SysPowerSyncUploadController.java` | `HIGH` |
| `reading_child_usage_daily` | `paipai` | `app_paipai_record` | `ReadingChildUsageDailyEntity.java` | `V1__init.sql` | `ReadingChildUsageDailyEntity.java`<br>`ReadingChildUsageDailyMapper.java` | `HIGH` |
| `reading_cloud_service_usage` | `paipai` | `app_paipai_record` | `ReadingCloudServiceUsageEntity.java` | `V1__init.sql` | `ReadingCloudServiceUsageEntity.java`<br>`ReadingCloudServiceUsageMapper.java` | `HIGH` |
| `reading_daily_task_completion` | `paipai` | `app_paipai_record` | `ReadingDailyTaskCompletionEntity.java` | `V1__init.sql` | `ReadingDailyTaskCompletionEntity.java`<br>`ReadingDailyTaskCompletionMapper.java` | `HIGH` |
| `reading_feedback_ticket` | `paipai` | `app_paipai_record` | `ReadingFeedbackTicketEntity.java` | `V1__init.sql` | `ReadingFeedbackTicketEntity.java` | `HIGH` |
| `reading_ocr_audit` | `paipai` | `app_paipai_record` | `ReadingOcrAuditEntity.java` | `V1__init.sql` | `ReadingOcrAuditEntity.java` | `HIGH` |
| `reading_review_card` | `paipai` | `app_paipai_record` | `ReadingReviewCardEntity.java` | `V1__init.sql` | `ReadingReviewCardEntity.java`<br>`ReadingReviewCardMapper.java` | `HIGH` |
| `reading_review_event` | `paipai` | `app_paipai_record` | `ReadingReviewEventEntity.java` | `V1__init.sql` | `ReadingReviewEventEntity.java`<br>`ReadingReviewEventMapper.java` | `HIGH` |
| `reading_review_event_v2` | `paipai` | `app_paipai_record` | `ReadingReviewEventV2Entity.java` | `V1__init.sql` | `ReadingReviewEventV2Entity.java`<br>`ReadingReviewEventV2Mapper.java` | `HIGH` |
| `reading_usage_session` | `paipai` | `app_paipai_record` | `ReadingUsageSessionEntity.java` | `V1__init.sql` | `ReadingUsageSessionEntity.java`<br>`ReadingUsageSessionMapper.java` | `HIGH` |
| `reading_usage_session_v2` | `paipai` | `app_paipai_record` | `ReadingUsageSessionV2Entity.java` | `V1__init.sql` | `ReadingUsageSessionV2Entity.java`<br>`ReadingUsageSessionV2Mapper.java` | `HIGH` |
| `reading_user_preference` | `paipai` | `app_paipai_record` | `ReadingUserPreferenceEntity.java` | `V1__init.sql` | `ReadingUserPreferenceEntity.java` | `HIGH` |
| `reading_weekly_report_snapshot` | `paipai` | `app_paipai_record` | `ReadingWeeklyReportSnapshotEntity.java` | `V19__paipai_p1_privacy_and_weekly_snapshot.sql` | `ReadingWeeklyReportSnapshotEntity.java`<br>`ReadingWeeklyReportSnapshotMapper.java` | `HIGH` |
| `saving_expense_record` | `saving` | `app_saving_record` | - | `V20__saving_launch_compat.sql` | `SavingFinanceMapper.java` | `HIGH` |
| `saving_saving_record` | `saving` | `app_saving_record` | - | `V20__saving_launch_compat.sql` | `SavingFinanceMapper.java` | `HIGH` |
| `sys_app` | `common` | `sys_common_record` | - | `V1__init.sql` | - | `MEDIUM` |
| `sys_app_store_notification` | `common` | `sys_common_record` | `SysAppStoreNotificationEntity.java` | `V1__init.sql` | `FitMysteryAccountMapper.java`<br>`SysAppStoreNotificationEntity.java`<br>`SysAppStoreNotificationMapper.java` | `HIGH` |
| `sys_audit_log` | `common` | `sys_common_record` | - | `V1__init.sql` | - | `MEDIUM` |
| `sys_auth_provider_token` | `common` | `sys_common_record` | `SysAuthProviderTokenEntity.java` | `V1__init.sql` | `SysAuthProviderTokenEntity.java`<br>`SysAuthProviderTokenMapper.java` | `HIGH` |
| `sys_auth_session` | `common` | `sys_common_record` | `SysAuthSessionEntity.java` | `V1__init.sql` | `SysAuthSessionEntity.java`<br>`SysAuthSessionMapper.java` | `HIGH` |
| `sys_email_verification_ticket` | `common` | `sys_common_record` | `SysEmailVerificationTicketEntity.java` | `V1__init.sql` | `SysEmailVerificationTicketEntity.java`<br>`SysEmailVerificationTicketMapper.java` | `HIGH` |
| `sys_entitlement_feature` | `common` | `sys_common_record` | `SysEntitlementFeatureEntity.java` | `V35__sys_entitlement_center_mode3.sql` | `SysEntitlementFeatureEntity.java` | `HIGH` |
| `sys_entitlement_policy_audit_log` | `common` | `sys_common_record` | - | `V35__sys_entitlement_center_mode3.sql` | - | `MEDIUM` |
| `sys_entitlement_snapshot` | `common` | `sys_common_record` | `SysEntitlementSnapshotEntity.java` | `V1__init.sql` | `FitMysteryAccountMapper.java`<br>`SysEntitlementSnapshotEntity.java`<br>`SysBillingService.java`<br>`SysEntitlementCenterController.java`<br>`SysUserEntitlementGrantEntity.java`<br>`SysUserPlanSnapshotEntity.java` | `HIGH` |
| `sys_membership_plan` | `common` | `sys_common_record` | `SysMembershipPlanEntity.java` | `V35__sys_entitlement_center_mode3.sql` | `SysMembershipPlanEntity.java` | `HIGH` |
| `sys_plan_feature_rule` | `common` | `sys_common_record` | `SysPlanFeatureRuleEntity.java` | `V35__sys_entitlement_center_mode3.sql` | `SysPlanFeatureRuleEntity.java` | `HIGH` |
| `sys_product_entitlement_mapping` | `common` | `sys_common_record` | `SysProductEntitlementMappingEntity.java` | `V35__sys_entitlement_center_mode3.sql` | `SysBillingService.java`<br>`SysProductEntitlementMappingEntity.java`<br>`SysEntitlementCenterService.java` | `HIGH` |
| `sys_purchase_transaction` | `common` | `sys_common_record` | `SysPurchaseTransactionEntity.java` | `V1__init.sql` | `FitMysteryAccountMapper.java`<br>`SysPurchaseTransactionEntity.java`<br>`SysPurchaseTransactionMapper.java` | `HIGH` |
| `sys_remote_config` | `common` | `sys_common_record` | `SysRemoteConfigEntity.java` | `V1__init.sql` | `AppVersionPolicyService.java`<br>`FitMysteryConfigService.java`<br>`ReadingUsagePolicyService.java`<br>`ReadingWeeklyReportAccessConfigService.java`<br>`ReadingCloudProviderConfigService.java`<br>`SavingConfigService.java`<br>`SysRemoteConfigEntity.java` | `HIGH` |
| `sys_sync_audit_log` | `common` | `sys_common_record` | `SysSyncAuditLogEntity.java` | `V1__init.sql` | `SysSyncAuditLogEntity.java` | `HIGH` |
| `sys_sync_installation` | `common` | `sys_common_record` | `SysSyncInstallationEntity.java` | `V1__init.sql` | `SysSyncInstallationEntity.java`<br>`SysSyncInstallationMapper.java` | `HIGH` |
| `sys_user` | `common` | `sys_common_record` | `SysUserEntity.java` | `V1__init.sql` | `SavingAccountDeletionService.java`<br>`SysUserEntity.java` | `HIGH` |
| `sys_user_device_event` | `common` | `sys_common_record` | `SysUserDeviceEventEntity.java` | `V1__init.sql` | `SysUserDeviceEventEntity.java` | `HIGH` |
| `sys_user_entitlement_grant` | `common` | `sys_common_record` | `SysUserEntitlementGrantEntity.java` | `V35__sys_entitlement_center_mode3.sql` | `SysUserEntitlementGrantEntity.java` | `HIGH` |
| `sys_user_feature_override` | `common` | `sys_common_record` | `SysUserFeatureOverrideEntity.java` | `V35__sys_entitlement_center_mode3.sql` | `SysUserFeatureOverrideEntity.java` | `HIGH` |
| `sys_user_identity` | `common` | `sys_common_record` | `SysUserIdentityEntity.java` | `V1__init.sql` | `SysUserIdentityEntity.java`<br>`SysUserIdentityMapper.java` | `HIGH` |
| `sys_user_plan_snapshot` | `common` | `sys_common_record` | `SysUserPlanSnapshotEntity.java` | `V35__sys_entitlement_center_mode3.sql` | `SysBillingService.java`<br>`SysUserPlanSnapshotEntity.java`<br>`SysUserPlanSnapshotMapper.java` | `HIGH` |
