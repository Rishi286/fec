package com.fec.wildlife.fog;

/** One timestamped sample as received from a sensor's /ingest batch. */
public record Reading(String ts, double value) {}
