package com.xq;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 生产-能源交互式优化平台后端启动类.
 *
 * @author XQ
 * @since 1.0.0
 */
@SpringBootApplication
@MapperScan("com.xq.mapper")
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
