package com.mandao.grc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * GRC 平台 · 模块化单体启动类。
 * 当前为「组织隔离 + RLS 兜底」垂直切片（M0 技术验证）。
 */
@SpringBootApplication
public class GrcApplication {
    public static void main(String[] args) {
        SpringApplication.run(GrcApplication.class, args);
    }
}
