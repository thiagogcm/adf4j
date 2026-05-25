package dev.nthings.adf4j.ast;

public record MediaAttrs(
    String type,
    String id,
    String localId,
    String url,
    String collection,
    String alt,
    String width,
    String height,
    String mediaType,
    String fileMimeType,
    String fileName,
    String name) {}
