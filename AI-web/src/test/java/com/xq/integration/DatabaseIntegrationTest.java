package com.xq.integration;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DatabaseIntegrationTest {

    /**
     * 用 H2 内存库模拟真实数据库，验证核心 SQL 查询能跑通。
     * <p>
     * 这里不依赖本机 MySQL，主要检查用户角色关联、能源方案聚合和碳减排汇总这些基础链路。
     * </p>
     */
    @Test
    void userRoleEnergyAndReportQueriesRunAgainstRealDatabase() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:ai_challenge;MODE=MySQL;DATABASE_TO_UPPER=false")) {
            createSchema(connection);
            seedData(connection);

            try (Statement statement = connection.createStatement()) {
                ResultSet roleResult = statement.executeQuery("""
                        SELECT u.username, r.role_code
                        FROM sys_user u
                        JOIN sys_user_role ur ON ur.user_id = u.id
                        JOIN sys_role r ON r.id = ur.role_id
                        WHERE u.deleted = 0 AND r.status = 'ENABLE'
                        ORDER BY r.role_code
                        """);
                roleResult.next();
                assertEquals("energy", roleResult.getString("username"));
                assertEquals("ENERGY_MANAGER", roleResult.getString("role_code"));

                ResultSet energyResult = statement.executeQuery("""
                        SELECT p.id, SUM(d.electricity_consumption) AS electricity, SUM(d.energy_cost) AS cost
                        FROM energy_plan p
                        JOIN energy_plan_detail d ON d.plan_id = p.id
                        WHERE p.plan_date = DATE '2026-07-17'
                        GROUP BY p.id
                        """);
                energyResult.next();
                assertEquals(200L, energyResult.getLong("id"));
                assertEquals(0, new BigDecimal("180.00").compareTo(energyResult.getBigDecimal("electricity")));
                assertEquals(0, new BigDecimal("147.00").compareTo(energyResult.getBigDecimal("cost")));

                ResultSet carbonResult = statement.executeQuery("""
                        SELECT SUM(carbon_reduction) AS total_carbon_reduction
                        FROM report_statistic
                        WHERE stat_type = 'DAY'
                        """);
                carbonResult.next();
                assertEquals(0, new BigDecimal("3.0000").compareTo(carbonResult.getBigDecimal("total_carbon_reduction")));
            }
        }
    }

    /**
     * 创建集成测试需要的最小表结构。
     * <p>
     * 只建本测试会用到的字段，避免测试被完整业务库结构拖慢。
     * </p>
     */
    private void createSchema(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE sys_user (
                      id BIGINT PRIMARY KEY,
                      username VARCHAR(64) NOT NULL,
                      password VARCHAR(255) NOT NULL,
                      status VARCHAR(32) NOT NULL DEFAULT 'ENABLE',
                      deleted TINYINT NOT NULL DEFAULT 0
                    )
                    """);
            statement.execute("""
                    CREATE TABLE sys_role (
                      id BIGINT PRIMARY KEY,
                      role_code VARCHAR(64) NOT NULL,
                      role_name VARCHAR(64) NOT NULL,
                      status VARCHAR(32) NOT NULL DEFAULT 'ENABLE'
                    )
                    """);
            statement.execute("""
                    CREATE TABLE sys_user_role (
                      id BIGINT PRIMARY KEY,
                      user_id BIGINT NOT NULL,
                      role_id BIGINT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE energy_plan (
                      id BIGINT PRIMARY KEY,
                      plan_date DATE NOT NULL,
                      status VARCHAR(32) NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE energy_plan_detail (
                      id BIGINT PRIMARY KEY,
                      plan_id BIGINT NOT NULL,
                      timestamp DATETIME NOT NULL,
                      electricity_consumption DECIMAL(18,4),
                      energy_cost DECIMAL(18,4)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE report_statistic (
                      id BIGINT PRIMARY KEY,
                      stat_date DATE NOT NULL,
                      stat_type VARCHAR(32) NOT NULL,
                      carbon_reduction DECIMAL(18,4)
                    )
                    """);
        }
    }

    /**
     * 插入固定测试数据。
     * <p>
     * 数据覆盖三类场景：用户角色、能源方案明细、日报表碳减排。
     * </p>
     */
    private void seedData(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO sys_user (id, username, password, status, deleted) VALUES (1, 'energy', '$2a$hash', 'ENABLE', 0)");
            statement.execute("INSERT INTO sys_role (id, role_code, role_name, status) VALUES (3, 'ENERGY_MANAGER', 'Energy Manager', 'ENABLE')");
            statement.execute("INSERT INTO sys_user_role (id, user_id, role_id) VALUES (11, 1, 3)");
            statement.execute("INSERT INTO energy_plan (id, plan_date, status) VALUES (200, DATE '2026-07-17', 'SUCCESS')");
            statement.execute("INSERT INTO energy_plan_detail (id, plan_id, timestamp, electricity_consumption, energy_cost) VALUES (1, 200, TIMESTAMP '2026-07-17 00:00:00', 60.00, 21.00)");
            statement.execute("INSERT INTO energy_plan_detail (id, plan_id, timestamp, electricity_consumption, energy_cost) VALUES (2, 200, TIMESTAMP '2026-07-17 18:00:00', 120.00, 126.00)");
            statement.execute("INSERT INTO report_statistic (id, stat_date, stat_type, carbon_reduction) VALUES (1, DATE '2026-07-17', 'DAY', 1.2500)");
            statement.execute("INSERT INTO report_statistic (id, stat_date, stat_type, carbon_reduction) VALUES (2, DATE '2026-07-18', 'DAY', 1.7500)");
        }
    }
}
