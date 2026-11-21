package dev.nthings.adf4j.internal.render;

import dev.nthings.adf4j.internal.AttachmentInventory;
import dev.nthings.adf4j.internal.analyze.HeadingOutline;
import dev.nthings.adf4j.internal.resource.ResourceResolver;
import dev.nthings.adf4j.options.DocumentContext;
import dev.nthings.adf4j.options.UnknownNodePolicy;

/// Renderer-neutral per-call state prepared by the pipeline: heading outline, document context,
/// unknown-node policy, indexed attachments, guarded resource resolver, and shared event sink.
public record RenderEnvironment(
    HeadingOutline headingOutline,
    DocumentContext context,
    UnknownNodePolicy unknownNodePolicy,
    AttachmentInventory attachments,
    ResourceResolver resources,
    RenderEvents events) {}
