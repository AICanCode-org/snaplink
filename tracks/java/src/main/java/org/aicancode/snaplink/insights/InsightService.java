package org.aicancode.snaplink.insights;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Uses the LLM when configured; any failure falls back to the template. */
public class InsightService {

    private static final Logger LOG = LoggerFactory.getLogger(InsightService.class);

    private final InsightGenerator template;
    private final InsightGenerator llm;

    public InsightService(InsightGenerator template, InsightGenerator llm) {
        this.template = template;
        this.llm = llm;
    }

    public InsightResult summarise(InsightInput data) {
        if (llm != null) {
            try {
                return new InsightResult("llm", false, llm.generate(data));
            } catch (Exception e) {
                LOG.warn("LLM insight failed, using template: {}", e.toString());
                return new InsightResult("template", true, templateText(data));
            }
        }
        return new InsightResult("template", false, templateText(data));
    }

    private String templateText(InsightInput data) {
        try {
            return template.generate(data);
        } catch (Exception e) {
            throw new IllegalStateException("template generator must not fail", e);
        }
    }
}
