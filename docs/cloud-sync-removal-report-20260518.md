# Cloud Sync Removal Report

## Scope

- Removed iOS cloud sync UI entry points, status badge, settings page, PowerSync API calls, and PowerSync Swift package dependency.
- Replaced the iOS repository database dependency with a local SQLite wrapper.
- Removed backend PowerSync controllers, services, models, mappers, app adapter, tests, deployment files, and release-gate adapter checks.
- Added database migration `V48__remove_cloud_sync.sql` to back up and drop sync tables, sync fields, entitlement feature rules, and remote config entries.
- Updated legal resources bundled in backend and iOS to remove cloud-sync wording.
- Removed remaining cloud-sync fields from backend preference/review-card DTOs, entities, tests, and first-version database snapshots.
- Removed cloud-sync mentions from current iOS onboarding, Info.plist, static prototypes, paywall prototype, parent-area prototype, and legal copies.
- Removed stale PowerSync expectations from backend contract audit, multi-app scaffolding docs, and historical knowledge-base Markdown.
- Renamed the iOS local SQLite table-name helper from `ReadingSyncTableName` to `ReadingLocalTableName` so local persistence no longer carries sync terminology.
- Added `backend/scripts/audit-cloud-sync-removal.sh` as a dedicated no-residue regression check across active code, scripts, product docs, and templates.

## Impact

- Learning content remains local on device for child profiles, review cards, learning events, usage sessions, entitlement cache, and local weekly reports.
- Backend authentication, billing, entitlement, OCR/TTS placeholders, announcements, account deletion, compensation, and weekly report flows remain available.
- Cloud sync API endpoints under `/api/v1/powersync/{appCode}/...` are no longer registered.
- PowerSync runtime SDK is no longer linked into the iOS target.
- Review cards, child profiles, usage sessions, review events, and preferences no longer expose sync installation IDs or cloud-sync toggles in current entities/DTOs.
- Existing deployments should apply `V48__remove_cloud_sync.sql` and `V49__purge_cloud_sync_backup_tables.sql`; sync tables, backup copies, fields, feature rules, and remote config entries are removed.

## Verification

- iOS build: `xcodebuild -project PaipaiReadAlong.xcodeproj -scheme PaipaiReadAlong -destination 'platform=iOS Simulator,name=iPhone 17,OS=26.5' build`
- Backend compile/tests: `MAVEN_SKIP_RC=true JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home /opt/homebrew/Cellar/maven/3.8.4/libexec/bin/mvn -q test`
- Static audit: runtime/source directories are clean for `PowerSync|powersync|cloudSync|Cloud Sync|云同步|sync_enabled|storage_mode|last_modified_by_installation_id|server_synced|server_authoritative|cloud_sync`; `V48__remove_cloud_sync.sql` is intentionally excluded because it names the removed database objects.
- Dedicated audit: `backend/scripts/audit-cloud-sync-removal.sh`.
