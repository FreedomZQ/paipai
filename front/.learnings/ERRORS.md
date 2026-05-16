# Errors

Command failures and integration errors.

---

## [ERR-20260422-001] ripgrep-missing
**Logged**: 2026-04-22T07:49:00+08:00
**Priority**: medium
**Status**: pending
**Area**: infra

### Summary
Tried to use rg for project-wide code search but ripgrep is not installed on this host.

### Error
```
/bin/bash: rg: command not found
```

### Context
- Operation attempted: project-wide code search in paipai/backend
- Environment: /home/admin/code/app on host iZ0xi7ocwfooxa1tq50mu7Z

### Suggested Fix
Install ripgrep on the host or keep using grep/find fallback.

### Metadata
- Reproducible: yes
- Related Files: n/a

---
