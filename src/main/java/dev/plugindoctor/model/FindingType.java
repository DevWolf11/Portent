package dev.plugindoctor.model;

public enum FindingType {
    MISSING_METHOD(Severity.ERROR),
    MISSING_FIELD(Severity.ERROR);

    private final Severity severity;

    FindingType(Severity severity) {
        this.severity = severity;
    }

    public Severity severity() {
        return severity;
    }
}
