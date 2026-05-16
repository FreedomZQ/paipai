# Auth DataService Migration Follow-up

Date: 2026-04-30 08:56 local

## Scope

Continued the MyBatis-Plus reusable data-access migration after the prior saving + FitMystery pass.

## Changes

- Added `SysAuthDataService` / `SysAuthDataServiceImpl` as the reusable data-access boundary for `sys_user`, `sys_user_identity`, `sys_auth_session`, and `sys_auth_provider_token`.
- Refactored these callers away from direct auth Mapper dependencies:
  - `SysAuthSessionService`
  - `SysAppleAuthService`
  - `ReadingAuthenticatedUserResolver`
  - `SysPowerSyncSessionService`
  - `SavingAccountDeletionService`
  - `FitMysteryAccountService`
  - `SystemController` token-storage observability
- Updated `audit-mybatis-plus-data-access.sh` so `*DataServiceImpl` classes are not counted as direct-mapper backlog.

## Validation

- PASS: backend compile with `mvn -q -DskipTests compile` (Docker Maven fallback used if host Maven is absent).
- PASS_WITH_MIGRATION_BACKLOG: `bash backend/scripts/audit-mybatis-plus-data-access.sh`
- Latest audit log: `backend/files/mybatis_plus_data_access_audit_20260430_085623.log`

## Remaining backlog

The remaining direct Mapper dependencies are now concentrated in:

- Paipai/Reading compatibility and PowerSync services
- Smaller sys common modules: billing, config center, app store notification, email verification, sync installation/audit

