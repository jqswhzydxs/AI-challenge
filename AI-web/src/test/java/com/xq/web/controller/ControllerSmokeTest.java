package com.xq.web.controller;

import com.xq.common.result.Result;
import com.xq.model.dto.LoginDTO;
import com.xq.model.vo.JointOptimizeEvaluationVO;
import com.xq.model.vo.LoadForecastVO;
import com.xq.model.vo.LoginVO;
import com.xq.model.vo.RealtimeControlImportResultVO;
import com.xq.model.vo.ReportEnergyAnalysisVO;
import com.xq.model.vo.SystemRoleVO;
import com.xq.model.vo.SystemUserVO;
import com.xq.service.AuthService;
import com.xq.service.EnergyDataService;
import com.xq.service.EnergyPlanService;
import com.xq.service.JointOptimizationService;
import com.xq.service.RealtimeControlService;
import com.xq.service.ReportService;
import com.xq.service.SystemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ControllerSmokeTest {

    private AuthService authService;
    private EnergyPlanService energyPlanService;
    private JointOptimizationService jointOptimizationService;
    private RealtimeControlService realtimeControlService;
    private ReportService reportService;
    private SystemService systemService;

    private MockMvc authMvc;
    private MockMvc energyMvc;
    private MockMvc optimizeMvc;
    private MockMvc realtimeControlMvc;
    private MockMvc reportMvc;
    private MockMvc systemMvc;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        EnergyDataService energyDataService = mock(EnergyDataService.class);
        energyPlanService = mock(EnergyPlanService.class);
        jointOptimizationService = mock(JointOptimizationService.class);
        realtimeControlService = mock(RealtimeControlService.class);
        reportService = mock(ReportService.class);
        systemService = mock(SystemService.class);

        authMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService)).build();
        energyMvc = MockMvcBuilders.standaloneSetup(new EnergyController(energyDataService, energyPlanService)).build();
        optimizeMvc = MockMvcBuilders.standaloneSetup(new OptimizeController(jointOptimizationService)).build();
        realtimeControlMvc = MockMvcBuilders.standaloneSetup(new RealtimeControlController(realtimeControlService)).build();
        reportMvc = MockMvcBuilders.standaloneSetup(new ReportController(reportService)).build();
        systemMvc = MockMvcBuilders.standaloneSetup(new SystemController(systemService)).build();
    }

    @Test
    void loginReturnsUnifiedResult() throws Exception {
        LoginVO loginVO = LoginVO.builder()
                .token("token")
                .userId(1L)
                .username("admin")
                .role("SYSTEM_ADMIN")
                .build();
        when(authService.login(any(LoginDTO.class))).thenReturn(Result.ok("登录成功", loginVO));

        authMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data.username", is("admin")));
    }

    @Test
    void energyLoadForecastEndpointReturnsSummary() throws Exception {
        when(energyPlanService.getLoadForecast(eq("2026-07-17")))
                .thenReturn(Result.ok(com.xq.model.vo.EnergyLoadForecastVO.builder()
                        .summary(LoadForecastVO.builder()
                                .peakHour("18:00-19:00")
                                .peakLoad(new BigDecimal("1200.00"))
                                .avgLoad(new BigDecimal("900.00"))
                                .build())
                        .build()));

        energyMvc.perform(get("/api/energy/load-forecast").param("planDate", "2026-07-17"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data.summary.peakHour", is("18:00-19:00")));
    }

    @Test
    void optimizeEvaluationEndpointReturnsMetrics() throws Exception {
        when(jointOptimizationService.getEvaluation(1L))
                .thenReturn(Result.ok(JointOptimizeEvaluationVO.builder()
                        .optimizeId(1L)
                        .mape(new BigDecimal("3.55"))
                        .er(new BigDecimal("96.45"))
                        .conflictCount(0)
                        .build()));

        optimizeMvc.perform(get("/api/optimize/evaluation/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data.optimizeId", is(1)))
                .andExpect(jsonPath("$.data.conflictCount", is(0)));
    }

    @Test
    void reportEnergyAnalysisEndpointReturnsSummary() throws Exception {
        when(reportService.getEnergyAnalysis(any()))
                .thenReturn(Result.ok(ReportEnergyAnalysisVO.builder()
                        .statisticCount(7L)
                        .totalEnergyKgce(new BigDecimal("1234.56"))
                        .energyKgcePerTon(new BigDecimal("2.34"))
                        .build()));

        reportMvc.perform(get("/api/reports/energy-analysis")
                        .param("startDate", "2026-07-01")
                        .param("endDate", "2026-07-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data.statisticCount", is(7)));
    }

    @Test
    void realtimeControlImportReturnsCompatibleFields() throws Exception {
        when(realtimeControlService.importRealtimeControl(any(), eq("2026-07-17"), eq("realtime_control.json")))
                .thenReturn(Result.ok("import success", RealtimeControlImportResultVO.builder()
                        .taskId(11L)
                        .success(true)
                        .latestControlId(22L)
                        .controlId(22L)
                        .insertedCount(1)
                        .updatedCount(0)
                        .totalCount(1)
                        .build()));

        realtimeControlMvc.perform(post("/api/realtime-control/import")
                        .param("controlDate", "2026-07-17")
                        .param("sourceFileName", "realtime_control.json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timestamp\":\"12:00:00\",\"control\":{\"boiler_load\":1,\"turbine_output\":2,\"grid_purchase\":3,\"power_factor_target\":0.95},\"forecast\":{\"elec_next_5min\":4,\"steam_next_5min\":5}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data.success", is(true)))
                .andExpect(jsonPath("$.data.controlId", is(22)))
                .andExpect(jsonPath("$.data.latestControlId", is(22)));
    }

    @Test
    void systemRolesEndpointReturnsRoles() throws Exception {
        when(systemService.listRoles(eq("ENABLE")))
                .thenReturn(Result.ok(List.of(SystemRoleVO.builder()
                        .id(1L)
                        .roleCode("SYSTEM_ADMIN")
                        .roleName("系统管理员")
                        .status("ENABLE")
                        .build())));

        systemMvc.perform(get("/api/system/roles").param("status", "ENABLE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data[0].roleCode", is("SYSTEM_ADMIN")));
    }

    @Test
    void systemCreateUserEndpointReturnsCreatedUser() throws Exception {
        when(systemService.createUser(any()))
                .thenReturn(Result.ok(SystemUserVO.builder()
                        .id(2L)
                        .username("planner")
                        .status("ENABLE")
                        .build()));

        systemMvc.perform(post("/api/system/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"planner\",\"password\":\"123456\",\"roleIds\":[1]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data.username", is("planner")))
                .andExpect(jsonPath("$.data.status", is("ENABLE")));
    }
}
