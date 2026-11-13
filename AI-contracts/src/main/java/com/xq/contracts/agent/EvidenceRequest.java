package com.xq.contracts.agent;

import java.time.LocalDate;

public record EvidenceRequest(String group, Long subjectId, LocalDate businessDate) {}
