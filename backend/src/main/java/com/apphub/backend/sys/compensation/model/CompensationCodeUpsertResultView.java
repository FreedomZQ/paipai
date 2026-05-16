package com.apphub.backend.sys.compensation.model;

public record CompensationCodeUpsertResultView(
    CompensationCodeView code,
    String note
) {}
