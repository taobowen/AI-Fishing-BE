package com.aifishing.lake.processing;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.openai")
public class OpenAiProperties {

    private String apiKey = "";
    private String model = "gpt-4o";
    private String strategyModel = "gpt-4o";
    private int timeoutSeconds = 120;
    private int maxTokens = 4000;
    private boolean webSearchEnabled = false;
    private String strategyPromptVersion = "fishing-strategy-v1";

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

    public String getStrategyModel() {
        return strategyModel == null || strategyModel.isBlank() ? model : strategyModel;
    }

    public void setStrategyModel(String strategyModel) {
        this.strategyModel = strategyModel;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public boolean isWebSearchEnabled() {
        return webSearchEnabled;
    }

    public void setWebSearchEnabled(boolean webSearchEnabled) {
        this.webSearchEnabled = webSearchEnabled;
    }

    public String getStrategyPromptVersion() {
        return strategyPromptVersion;
    }

    public void setStrategyPromptVersion(String strategyPromptVersion) {
        this.strategyPromptVersion = strategyPromptVersion;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
