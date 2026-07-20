package com.xq.web.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j / OpenAPI 3 配置.
 * <p>
 * 启动后访问 http://localhost:8080/doc.html 查看接口文档.
 * </p>
 *
 * @author XQ
 * @since 1.0.0
 */
@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("生产-能源交互式优化平台 API")
                        .version("1.0.0")
                        .description("AI赋能降本增效——创新型生产-能源交互式优化技术 后端接口文档")
                        .contact(new Contact()
                                .name("后端组")
                                .email("dev@example.com")));
    }
}
