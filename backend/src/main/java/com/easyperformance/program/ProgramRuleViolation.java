package com.easyperformance.program;

/** Internal typed rule failure translated by the program controller boundary. */
public class ProgramRuleViolation extends RuntimeException {
    private final String rule;

    public ProgramRuleViolation(String rule, String message) {
        super(message);
        this.rule = rule;
    }

    public String rule() { return rule; }
}
