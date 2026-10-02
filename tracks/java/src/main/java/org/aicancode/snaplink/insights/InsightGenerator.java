package org.aicancode.snaplink.insights;

/** Strategy interface: template (offline) or LLM (optional). */
public interface InsightGenerator {

    String generate(InsightInput data) throws Exception;
}
