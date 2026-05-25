package dev.nthings.adf4j;

import java.util.Objects;

import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.model.ParseResult;

public final class AdfProcessor {

  private final AdfParsingService parsingService;
  private final AdfStorageDocumentService storageDocumentService;
  private final AdfPresentationDocumentService presentationDocumentService;

  public AdfProcessor() {
    this(AdfServices.createDefault());
  }

  AdfProcessor(AdfServices services) {
    this(
        services.parsingService(),
        services.storageDocumentService(),
        services.presentationDocumentService());
  }

  public AdfProcessor(
      AdfParsingService parsingService,
      AdfStorageDocumentService storageDocumentService,
      AdfPresentationDocumentService presentationDocumentService) {
    this.parsingService = parsingService;
    this.storageDocumentService = storageDocumentService;
    this.presentationDocumentService = presentationDocumentService;
  }

  public ParseResult parse(String rawAdf) {
    return parsingService.parse(rawAdf);
  }

  public RenderResult renderStorageDocument(String rawAdf, RenderOptions options) {
    return storageDocumentService.renderStorageDocument(rawAdf, Objects.requireNonNull(options, "options"));
  }

  public String renderStorageMarkdown(String rawAdf) {
    return renderStorageMarkdown(rawAdf, RenderOptions.defaults(""));
  }

  public String renderStorageMarkdown(String rawAdf, RenderOptions options) {
    return storageDocumentService.renderStorageMarkdown(rawAdf, Objects.requireNonNull(options, "options"));
  }

  public String renderStorageMarkdown(AdfDocument document, RenderOptions options) {
    return storageDocumentService.renderStorageMarkdown(document, Objects.requireNonNull(options, "options"));
  }

  public ContentMetadata extractContentMetadata(String rawAdf, RenderOptions options) {
    return storageDocumentService.extractContentMetadata(rawAdf, Objects.requireNonNull(options, "options"));
  }

  public ContentMetadata extractContentMetadata(AdfDocument document, RenderOptions options) {
    return storageDocumentService.extractContentMetadata(document, Objects.requireNonNull(options, "options"));
  }

  public String renderPresentationMarkdown(String rawAdf, RenderOptions options) {
    return presentationDocumentService.renderPresentationMarkdown(rawAdf, Objects.requireNonNull(options, "options"));
  }

  public String renderPresentationMarkdown(AdfDocument document, RenderOptions options) {
    return presentationDocumentService.renderPresentationMarkdown(document, Objects.requireNonNull(options, "options"));
  }

  public String renderPresentationHtml(String rawAdf, RenderOptions options) {
    return presentationDocumentService.renderPresentationHtml(rawAdf, Objects.requireNonNull(options, "options"));
  }
}
