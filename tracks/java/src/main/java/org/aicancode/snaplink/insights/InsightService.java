package org.aicancode.snaplink.insights;

/** Uses the LLM when configured; any failure falls back to the template. */
public class InsightService {

    private final InsightGenerator template;
    private final InsightGenerator llm;

    public InsightService(InsightGenerator template, InsightGenerator llm) {
        this.template = template;
        this.llm = llm;
    }

    public InsightResult summarise(InsightInput data) {
        // TODO(phase-3 M5): llm == null -> template (fallback false). Otherwise try llm.generate -> provider "llm";
        // on ANY exception log a warning (SLF4J) and return the template text with fallback true.
        throw new UnsupportedOperationException("TODO(phase-3 M5): InsightService.summarise");
    }
}
