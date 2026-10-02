package org.aicancode.snaplink.insights;

/** provider is "template" or "llm"; fallback=true when the LLM failed and the template was used. */
public record InsightResult(String provider, boolean fallback, String summary) {
}
