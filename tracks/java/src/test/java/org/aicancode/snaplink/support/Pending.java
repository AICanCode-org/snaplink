package org.aicancode.snaplink.support;

import java.util.Map;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * PENDING test classes describe behaviour you have not built yet, so they are skipped
 * (Maven reports them as "Skipped"). When you start a milestone, delete its line from
 * {@link #PENDING}, watch the tests fail (red), implement until they pass (green).
 * Registered for every test automatically (see src/test/resources/junit-platform.properties).
 */
public class Pending implements ExecutionCondition {

    /** Test class simple name -> milestone. */
    static final Map<String, String> PENDING = Map.of(
            "ShortCodeGeneratorTest", "Phase 3 M1 - short codes",
            "ValidationTest", "Phase 3 M1 - validation",
            "LinkServiceTest", "Phase 3 M2-M4 - LinkService",
            "ApiTest", "Phase 3 M2-M4 - HTTP API",
            "InsightsTest", "Phase 3 M5 - insights",
            "ApiIntegrationTest", "Phase 4 - integration tests");

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        return context.getTestClass()
                .map(c -> PENDING.get(c.getSimpleName()))
                .map(reason -> ConditionEvaluationResult.disabled("PENDING (" + reason + ") - see support/Pending.java"))
                .orElse(ConditionEvaluationResult.enabled("not pending"));
    }
}
