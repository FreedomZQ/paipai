package com.apphub.backend.sys.billing.privacy.model;

import java.util.List;

public record EntitlementEventReportView(
    List<String> accepted,
    List<RejectedEvent> rejected,
    Long serverEntitlementVersion,
    boolean refreshRequired
) {
    public record RejectedEvent(String eventId, String reasonCode, String message) {
    }
}
