package dev.nthings.adf4j.result;

public record ParseIssue(String code, String message, Throwable cause) {}
