package com.xq.web.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JacksonConfigTest {

    /**
     * 验证后端接口返回时，Long ID 会变成字符串，BigDecimal 仍保持数字。
     */
    @Test
    void serializesLongAsStringAndKeepsBigDecimalAsNumber() throws Exception {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new JacksonConfig().longToStringCustomizer().customize(builder);
        ObjectMapper mapper = builder.build();

        String json = mapper.writeValueAsString(new Payload());

        assertTrue(json.contains("\"scheduleId\":\"2081219222897274881\""));
        assertTrue(json.contains("\"taskId\":\"2081219222897274881\""));
        assertTrue(json.contains("\"elecCoefficient\":14.0000"));
        assertFalse(json.contains("parsedValue"));
    }

    private static class Payload {
        public Long scheduleId = 2081219222897274881L;
        public long taskId = 2081219222897274881L;
        public BigDecimal elecCoefficient = new BigDecimal("14.0000");
    }
}
