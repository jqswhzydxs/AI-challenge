package com.xq.web.controller;

import com.alibaba.fastjson2.JSON;
import com.xq.common.constant.UserRole;
import com.xq.common.exception.BusinessException;
import com.xq.contracts.agent.*;
import com.xq.mapper.EnergyPlanMapper;
import com.xq.mapper.JointOptimizationPlanMapper;
import com.xq.model.vo.LoginVO;
import com.xq.service.AuthService;
import com.xq.service.agent.IndustrialEvidenceClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Internal endpoints require BOTH a distinct service credential and a current user identity. */
@RestController
@RequestMapping("/internal/v1")
@RequiredArgsConstructor
public class InternalAgentController {
  private final AuthService authService;
  private final IndustrialEvidenceClient evidence;
  private final EnergyPlanMapper energyPlans;
  private final JointOptimizationPlanMapper optimizations;
  private final com.xq.mapper.ProductionSchedulePlanMapper schedules;

  @Value("${services.agent-token:}")
  private String serviceToken;

  @PostMapping("/auth/verify")
  public VerifiedIdentity verify(
      @RequestHeader(value = "X-Service-Token", defaultValue = "") String credential,
      @RequestHeader(value = "Authorization", defaultValue = "") String authorization) {
    LoginVO user = authenticate(credential, authorization);
    return new VerifiedIdentity(user.getUserId().toString(), user.getUsername(), user.getRoles());
  }

  @PostMapping("/agent/evidence-snapshots")
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public EvidenceBundle snapshot(
      @RequestHeader(value = "X-Service-Token", defaultValue = "") String credential,
      @RequestHeader(value = "Authorization", defaultValue = "") String authorization,
      @RequestBody EvidenceRequest request)
      throws Exception {
    LoginVO user = authenticate(credential, authorization);
    boolean admin = user.getRoles().stream().anyMatch(UserRole::isAdmin);
    if (request.group() == null) throw new BusinessException(400, "缺少证据场景");
    Map<String, ToolResult<Map<String, Object>>> results = new LinkedHashMap<>();
    String version;
    LocalDate date = request.businessDate();
    switch (request.group()) {
      case "optimization" -> {
        var subject = optimizations.selectById(positive(request.subjectId()));
        if (subject == null) throw new BusinessException(404, "协同方案不存在");
        owner(user, subject.getCreateBy(), admin);
        version =
            Objects.toString(
                subject.getUpdateTime(), Objects.toString(subject.getCreateTime(), "legacy"));
        var schedule = schedules.selectById(subject.getScheduleId());
        date = schedule == null ? null : schedule.getScheduleDate();
        results.put("evaluation", evidence.query("evaluation", request.subjectId(), null));
        results.put("conflicts", evidence.query("conflicts", request.subjectId(), null));
      }
      case "energyPlan" -> {
        var subject = energyPlans.selectById(positive(request.subjectId()));
        if (subject == null) throw new BusinessException(404, "能源方案不存在");
        owner(user, subject.getCreateBy(), admin);
        version =
            Objects.toString(
                subject.getUpdateTime(), Objects.toString(subject.getCreateTime(), "legacy"));
        date = subject.getPlanDate();
        results.put("energyPlan", evidence.query("energyPlan", request.subjectId(), date));
      }
      case "dailyReport" -> {
        if (!admin) throw new BusinessException(403, "工厂级汇总报表需要管理员权限");
        if (date == null) throw new BusinessException(400, "缺少业务日期");
        version = "report-content-hash";
        results.put("dailyReport", evidence.query("dailyReport", null, date));
      }
      default -> throw new BusinessException(400, "不支持的证据场景");
    }
    Map<String, Object> content = new LinkedHashMap<>();
    results.forEach(
        (tool, result) ->
            content.put(
                tool,
                java.util.Arrays.asList(
                    result.status(), result.data(), result.errorCode(), result.warnings())));
    String hash =
        HexFormat.of()
            .formatHex(MessageDigest.getInstance("SHA-256").digest(JSON.toJSONBytes(content)));
    return new EvidenceBundle(
        UUID.randomUUID().toString(),
        request.group(),
        request.subjectId() == null ? null : request.subjectId().toString(),
        version + ":" + hash,
        date,
        "Asia/Shanghai",
        hash,
        results);
  }

  private LoginVO authenticate(String credential, String authorization) {
    if (serviceToken.length() < 24
        || !MessageDigest.isEqual(
            serviceToken.getBytes(StandardCharsets.UTF_8),
            credential.getBytes(StandardCharsets.UTF_8))) {
      throw new BusinessException(401, "无效的服务身份");
    }
    return authService.currentUser(authorization).getData();
  }

  private long positive(Long id) {
    if (id == null || id <= 0) throw new BusinessException(400, "业务对象 ID 无效");
    return id;
  }

  private void owner(LoginVO user, Long creator, boolean admin) {
    if (!admin && (creator == null || !creator.equals(user.getUserId()))) {
      throw new BusinessException(403, "无权读取该业务对象；历史无归属数据仅管理员可读");
    }
  }
}
