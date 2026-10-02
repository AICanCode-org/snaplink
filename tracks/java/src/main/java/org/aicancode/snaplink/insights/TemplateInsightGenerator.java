package org.aicancode.snaplink.insights;

/** Deterministic, offline, free. Identical wording in all three tracks (spec/requirements.md FR-9). */
public class TemplateInsightGenerator implements InsightGenerator {

    @Override
    public String generate(InsightInput data) {
        // TODO(phase-3 M5): build the exact sentence from the spec: total clicks in the window,
        // busiest day (earliest wins ties), trend = last half vs first half of the window.
        throw new UnsupportedOperationException("TODO(phase-3 M5): TemplateInsightGenerator.generate");
    }
}
