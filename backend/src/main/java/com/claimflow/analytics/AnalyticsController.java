package com.claimflow.analytics;

import com.claimflow.events.ClaimEventsSocketHandler;
import com.claimflow.config.AppProperties;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analytics;
    private final ClaimEventsSocketHandler sockets;
    private final AppProperties props;

    public AnalyticsController(AnalyticsService analytics, ClaimEventsSocketHandler sockets, AppProperties props) {
        this.analytics = analytics;
        this.sockets = sockets;
        this.props = props;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADJUSTER','MANAGER','ADMIN')")
    public AnalyticsSummary summary() {
        return analytics.summary();
    }

    /** Which replica answered - handy for showing nginx load balancing in a demo. */
    @GetMapping("/instance")
    public Map<String, Object> instance() {
        return Map.of("instance", props.instanceId(), "websocketConnections", sockets.connectionCount());
    }
}
