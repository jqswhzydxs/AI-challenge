package com.xq.agent;

import com.xq.contracts.agent.AgentAdviceVO;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Facts and references are server-rendered. Narrative cannot add unverified numeric claims. */
@Component
public class AgentResponseValidator {
  private static final Pattern NUMERIC =
      Pattern.compile("[0-9０-９]|[零〇一二三四五六七八九十百千万亿两]+(?:[%％元吨天倍]|小时)");

  public List<String> validate(AgentAdviceVO model, AgentAdviceVO frozen) {
    if (model == null) return List.of("MODEL_EMPTY_RESPONSE");
    List<String> errors = new ArrayList<>();
    if (model.getSummary() == null || model.getSummary().isBlank())
      errors.add("MODEL_MISSING_SUMMARY");
    if (!List.of("LOW", "MEDIUM", "HIGH", "UNKNOWN")
        .contains(Objects.toString(model.getRiskLevel(), ""))) errors.add("MODEL_INVALID_RISK");
    else if (!Objects.equals(model.getRiskLevel(), frozen.getRiskLevel()))
      errors.add("MODEL_RISK_MISMATCH");
    if (!Objects.equals(model.getKeyMetrics(), frozen.getKeyMetrics()))
      errors.add("MODEL_METRIC_MISMATCH");
    if (!Objects.equals(model.getEvidence(), frozen.getEvidence()))
      errors.add("MODEL_EVIDENCE_MISMATCH");
    List<String> prose = new ArrayList<>();
    prose.add(model.getSummary());
    for (List<String> values :
        java.util.Arrays.asList(
            model.getProblems(), model.getSuggestions(), model.getNextActions())) {
      if (values != null) prose.addAll(values);
    }
    if (prose.stream()
        .filter(Objects::nonNull)
        .anyMatch(text -> NUMERIC.matcher(text.replace("tCO2", "")).find()))
      errors.add("MODEL_UNVERIFIED_NUMERIC_CLAIM");
    return List.copyOf(errors);
  }
}
