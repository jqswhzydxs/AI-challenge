package com.xq.web.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JSON 序列化配置。
 * <p>
 * 后端 ID 使用 MyBatis-Plus 雪花 Long，数值长度会超过 JavaScript 安全整数范围。
 * 这里统一把 Long 输出成字符串，避免前端解析时出现精度丢失。
 * </p>
 */
@Configuration
public class JacksonConfig {

    /**
     * 让所有 Long/long 字段按字符串输出。
     * <p>
     * BigDecimal 不做特殊处理，仍按普通 JSON 数字输出，方便前端图表和计算使用。
     * </p>
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longToStringCustomizer() {
        return builder -> {
            builder.serializerByType(Long.class, ToStringSerializer.instance);
            builder.serializerByType(Long.TYPE, ToStringSerializer.instance);
        };
    }
}
