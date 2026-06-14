package com.pingpongsmt.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Properties;

/**
 * 手动解析 .env 文件的工具类。
 * 逐行读取 KEY=VALUE 格式，跳过注释行（#开头）和空行，
 * 将结果通过 System.setProperty() 写入，供 Spring 读取。
 */
public class EnvLoader {

    private EnvLoader() {
    }

    /**
     * 从 resources/.env 加载环境变量。
     * 如果 .env 不存在则静默跳过（允许使用已设置的环境变量）。
     */
    public static void load() {
        // 尝试多个可能的 .env 路径
        String[] possiblePaths = {
                "backend/src/main/resources/.env",
                "src/main/resources/.env",
                ".env"
        };

        Properties props = new Properties();
        boolean loaded = false;

        for (String path : possiblePaths) {
            Path envFile = Path.of(path);
            if (Files.exists(envFile)) {
                try {
                    try (var lines = Files.lines(envFile)) {
                        lines.filter(line -> !line.trim().isEmpty())
                             .filter(line -> !line.trim().startsWith("#"))
                             .filter(line -> line.contains("="))
                             .forEach(line -> {
                                 int idx = line.indexOf('=');
                                 String key = line.substring(0, idx).trim();
                                 String value = line.substring(idx + 1).trim();
                                 props.setProperty(key, value);
                                 System.setProperty(key, value);
                             });
                    }
                    loaded = true;
                    System.out.println("[EnvLoader] Loaded .env from: " + path);
                } catch (IOException e) {
                    System.err.println("[EnvLoader] Failed to load " + path + ": " + e.getMessage());
                }
                break;
            }
        }

        if (!loaded) {
            System.out.println("[EnvLoader] No .env file found, using system/environment variables");
        }
    }
}
