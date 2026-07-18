package com.fec.wildlife.fog;

import java.util.List;

/** Plain carrier for one window's aggregate plus its fired alerts, serialized by AggregateSerializer. */
public record AggregatePayload(WindowAggregate window, List<String> alerts) {}
