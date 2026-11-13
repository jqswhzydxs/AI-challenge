package com.xq.contracts.task;

import java.time.LocalDate;
import java.util.List;

public record InputManifest(
    int schemaVersion,
    String requestId,
    LocalDate businessDate,
    String timezone,
    String algorithmVersion,
    String energyRef,
    String energySha256,
    String ordersRef,
    String ordersSha256,
    String trainingFrom,
    String trainingUntil,
    int recordCount,
    List<String> orderIds,
    String measurementType) {}
