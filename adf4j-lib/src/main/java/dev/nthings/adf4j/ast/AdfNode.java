package dev.nthings.adf4j.ast;

public sealed interface AdfNode permits AdfDocument, AdfBlock, AdfInline {}
