package com.pingpongsmt.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * LLM config - reads from environment variables set by EnvLoader.
 */
@Configuration
public class LlmConfig {

    @Value("${LLM_BASE_URL:https://maas-api.cn-huabei-1.xf-yun.com/v2}")
    private String baseUrl;

    @Value("${LLM_API_KEY:}")
    private String apiKey;

    @Value("${LLM_MODEL:xopqwen36v35b}")
    private String model;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
}
