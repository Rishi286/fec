// API base resolution: fetched once at startup from a small JSON resource
// deployed alongside this file, not baked in via a build-time token
// substitution, a <meta> tag, or a separate config-global script (the three
// mechanisms this portfolio's other Java/Python siblings already use). The
// deploy step overwrites static/api-config.json in S3 with the real API
// Gateway URL; locally (served by Tornado -- er, by WildlifeDashboardApp
// on :8000) the committed placeholder resolves to "", so fetch() calls fall
// back to same-origin relative paths exactly as before.
let API_BASE = "";

async function loadApiBase() {
  try {
    const res = await fetch("/static/api-config.json");
    if (res.ok) {
      const config = await res.json();
      API_BASE = config.apiBase || "";
    }
  } catch (e) {
    // api-config.json missing or unreachable; API_BASE stays "" (same-origin)
  }
}

const SENSOR_TYPES = ["motion_detection_count", "acoustic_poaching_risk_db", "waterhole_level_cm", "ambient_temp_c", "soil_moisture_pct"];

const METRIC_LABELS = {
  motion_detection_count: "Motion Events",
  acoustic_poaching_risk_db: "Acoustic Risk",
  waterhole_level_cm: "Waterhole Level",
  ambient_temp_c: "Ambient Temp",
  soil_moisture_pct: "Soil Moisture",
};

const ALERT_LABELS = {
  poaching_risk_detected: "POACHING RISK",
  drought_stress_risk: "DROUGHT STRESS",
  unusual_activity_surge: "ACTIVITY SURGE",
  habitat_dryness_risk: "HABITAT DRYNESS",
};

// Axis bounds -- the range each summary <meter> is drawn against, not a
// decision threshold. Real alert thresholds come from /api/thresholds.
const AXIS_RANGE = {
  motion_detection_count: { lo: 0, hi: 50 },
  acoustic_poaching_risk_db: { lo: 20, hi: 100 },
  waterhole_level_cm: { lo: 0, hi: 200 },
  ambient_temp_c: { lo: 10, hi: 45 },
  soil_moisture_pct: { lo: 0, hi: 100 },
};

const TREND_COLORS = { "reserve-a": "#2f4a37", "reserve-b": "#a5501f" };
let waterholeChart = null;

function metricLabel(sensorType) {
  return METRIC_LABELS[sensorType] || sensorType;
}

function formatClockTime(isoTimestamp) {
  try {
    return new Date(isoTimestamp).toLocaleTimeString("en-GB", { hour12: false });
  } catch (e) {
    return "--:--:--";
  }
}

function summaryRow(sensorType, m) {
  const label = metricLabel(sensorType);
  if (!m) {
    return `<div class="summary-row">
      <span class="summary-label">${label}</span>
      <meter min="0" max="1" value="0"></meter>
      <span class="summary-value">no data</span>
    </div>`;
  }
  const flagged = m.alerts && m.alerts.length > 0;
  const { lo, hi } = AXIS_RANGE[sensorType];
  return `<div class="summary-row${flagged ? " flagged" : ""}">
    <span class="summary-label">${label}</span>
    <meter min="${lo}" max="${hi}" value="${m.latest}"></meter>
    <span class="summary-value">${m.latest}${m.unit ? " " + m.unit : ""}</span>
  </div>`;
}

function renderSummary(containerId, reserve) {
  const container = document.getElementById(containerId);
  if (!reserve) {
    container.innerHTML = SENSOR_TYPES.map((t) => summaryRow(t, null)).join("");
    return;
  }
  container.innerHTML = SENSOR_TYPES.map((sensorType) => summaryRow(sensorType, reserve.metrics[sensorType])).join("");
}

// Primary structural view: a field-station LOG readout -- one row per
// window across every sensor type, merged and sorted newest-first, styled
// like a ranger-station notebook entry rather than a per-metric card.
function logRow(entry) {
  const flagged = entry.alerts && entry.alerts.length > 0;
  const flagText = flagged ? entry.alerts.map((a) => ALERT_LABELS[a] || a).join(", ") : "clear";
  return `<div class="log-row${flagged ? " flagged" : ""}">
    <span class="log-col-time">${formatClockTime(entry.window_end)}</span>
    <span class="log-col-sensor">${metricLabel(entry.sensor_type)}</span>
    <span class="log-col-value">avg ${entry.avg}${entry.unit ? " " + entry.unit : ""}</span>
    <span class="log-col-flag${flagged ? "" : " clear"}">${flagText}</span>
  </div>`;
}

function renderLog(containerId, reserve) {
  const container = document.getElementById(containerId);
  if (!reserve || !reserve.log || reserve.log.length === 0) {
    container.innerHTML = `<div class="log-empty">no log entries yet</div>`;
    return;
  }
  container.innerHTML = reserve.log.map(logRow).join("");
}

function renderAlertBanner(reserves) {
  const banner = document.getElementById("alert-banner");
  const active = [];
  for (const reserve of reserves) {
    for (const entry of reserve.log || []) {
      if (entry.alerts && entry.alerts.length > 0) {
        active.push(`${reserve.site_id}: ${entry.alerts.map((a) => ALERT_LABELS[a] || a).join(", ")}`);
      }
    }
  }
  if (active.length === 0) {
    banner.hidden = true;
    return;
  }
  banner.hidden = false;
  const unique = [...new Set(active)].slice(0, 6);
  banner.textContent = `${active.length} recent alert entry(ies) -- ${unique.join(" | ")}`;
}

function renderHealth(health) {
  const strip = document.getElementById("health-strip");
  const pill = (label, ok) => `<span class="health-pill ${ok ? "" : "down"}"><span class="swatch"></span>${label}</span>`;
  strip.innerHTML =
    pill("Gateway", health.gateway) +
    pill("Queue", health.queue) +
    pill("Lambda", health.lambda) +
    pill("Pipeline", health.pipeline);
}

function renderBackendStats(backendStats) {
  const el = document.getElementById("backend-stats");
  const queueInfo = backendStats.queue
    ? `${backendStats.queue.waiting} waiting / ${backendStats.queue.in_flight} in-flight`
    : "queue unknown";
  el.textContent = `${backendStats.items_in_table ?? 0} records stored -- ${queueInfo}`;
}

async function fetchTrend(sensorType) {
  const res = await fetch(`${API_BASE}/api/readings?sensor_type=${sensorType}&limit=20`);
  return res.json();
}

function renderWaterholeTrend(items) {
  const canvas = document.getElementById("trend-waterhole");
  if (!canvas) return;

  const bySite = {};
  for (const item of items) {
    (bySite[item.site_id] = bySite[item.site_id] || []).push(item);
  }

  const labels = items.filter((i) => i.site_id === Object.keys(bySite)[0]).map((_, i) => i);
  const datasets = Object.entries(bySite).map(([siteId, points]) => ({
    label: siteId,
    data: points.map((p) => p.avg),
    borderColor: TREND_COLORS[siteId] || "#999",
    backgroundColor: "transparent",
    borderWidth: 2,
    pointRadius: 0,
    tension: 0.3,
  }));

  if (waterholeChart) {
    waterholeChart.data.labels = labels.length ? labels : [0];
    waterholeChart.data.datasets = datasets;
    waterholeChart.update();
    return;
  }

  waterholeChart = new Chart(canvas, {
    type: "line",
    data: { labels: labels.length ? labels : [0], datasets },
    options: {
      animation: false,
      responsive: false,
      plugins: { legend: { display: true, labels: { boxWidth: 10, font: { size: 10 }, color: "#6b6f52" } } },
      scales: {
        x: { display: false },
        y: { display: true, ticks: { font: { size: 10 }, color: "#6b6f52" }, grid: { color: "#cabf95" } },
      },
    },
  });
}

async function tick() {
  try {
    const [reservesResp, health, backendStats] = await Promise.all([
      fetch(`${API_BASE}/api/reserves`).then((r) => r.json()),
      fetch(`${API_BASE}/api/health`).then((r) => r.json()),
      fetch(`${API_BASE}/api/backend-stats`).then((r) => r.json()),
    ]);

    const reserves = reservesResp.reserves || [];
    const reserveA = reserves.find((r) => r.site_id === "reserve-a");
    const reserveB = reserves.find((r) => r.site_id === "reserve-b");

    renderSummary("summary-reserve-a", reserveA);
    renderSummary("summary-reserve-b", reserveB);
    renderLog("log-reserve-a", reserveA);
    renderLog("log-reserve-b", reserveB);
    renderAlertBanner(reserves);
    renderHealth(health);
    renderBackendStats(backendStats);

    const waterholeTrend = await fetchTrend("waterhole_level_cm");
    renderWaterholeTrend(waterholeTrend.items || []);
  } catch (e) {
    // backend not ready yet; next tick retries
  }
}

loadApiBase().then(() => {
  tick();
  setInterval(tick, 2500);
});
