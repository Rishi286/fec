package com.fec.wildlife.dashboard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WildlifeDashboardAppTest {

    @Test
    void parseQueryHandlesMultipleParams() {
        var params = WildlifeDashboardApp.parseQuery("sensor_type=waterhole_level_cm&limit=10");
        assertEquals("waterhole_level_cm", params.get("sensor_type"));
        assertEquals("10", params.get("limit"));
    }

    @Test
    void parseQueryHandlesNullQueryString() {
        var params = WildlifeDashboardApp.parseQuery(null);
        assertEquals(0, params.size());
    }

    @Test
    void parseQueryUrlDecodesValues() {
        var params = WildlifeDashboardApp.parseQuery("site_id=reserve%20a");
        assertEquals("reserve a", params.get("site_id"));
    }

    @Test
    void contentTypeForResolvesKnownExtensions() {
        assertEquals("text/html", WildlifeDashboardApp.contentTypeFor("static/index.html"));
        assertEquals("application/javascript", WildlifeDashboardApp.contentTypeFor("static/dashboard.js"));
        assertEquals("text/css", WildlifeDashboardApp.contentTypeFor("static/style.css"));
        assertEquals("application/octet-stream", WildlifeDashboardApp.contentTypeFor("static/vendor/chart.umd.min.js.map"));
    }
}
