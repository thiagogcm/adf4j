package dev.nthings.adf4j.ast;

public record Mention(String id, String text, String userType, String accessLevel, String localId)
    implements AdfInline {}
