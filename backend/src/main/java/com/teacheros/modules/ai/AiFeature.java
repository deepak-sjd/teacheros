package com.teacheros.modules.ai;

/**
 * Every place the app calls the AI provider. configKey is the suffix of
 * app.ai.limits.<configKey> (per-tenant, per-day).
 */
public enum AiFeature {
    DOUBT("doubt", "AI tutor questions", 2000),
    STUDY_PLAN("study-plan", "study plans", 300),
    CONTENT_QUESTIONS("content-questions", "AI question drafts", 200),
    CONTENT_NOTES("content-notes", "AI notes drafts", 200);

    private final String configKey;
    private final String label;
    private final int defaultDailyLimit;

    AiFeature(String configKey, String label, int defaultDailyLimit) {
        this.configKey = configKey;
        this.label = label;
        this.defaultDailyLimit = defaultDailyLimit;
    }

    public String configKey() {
        return configKey;
    }

    public String label() {
        return label;
    }

    public int defaultDailyLimit() {
        return defaultDailyLimit;
    }
}
