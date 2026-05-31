package dev.nthings.adf4j.internal.engine;

import dev.nthings.adf4j.internal.parser.AdfAstParser;
import dev.nthings.adf4j.internal.render.AdfHeadingCollector;
import dev.nthings.adf4j.internal.render.AdfRendererFactory;

import tools.jackson.databind.json.JsonMapper;

/** Default dependency wiring shared by {@link dev.nthings.adf4j.AdfConverter}. */
public final class AdfServices {

  private final JsonMapper mapper;
  private final AdfAstParser astParser;
  private final AdfParsingService parsingService;
  private final AdfDocumentWorkflow workflow;

  private AdfServices(
      JsonMapper mapper,
      AdfAstParser astParser,
      AdfParsingService parsingService,
      AdfDocumentWorkflow workflow) {
    this.mapper = mapper;
    this.astParser = astParser;
    this.parsingService = parsingService;
    this.workflow = workflow;
  }

  public static AdfServices createDefault() {
    var mapper = JsonMapper.builder().build();
    var astParser = new AdfAstParser(mapper);
    var headingCollector = new AdfHeadingCollector();
    var renderer = AdfRendererFactory.adfRenderer(headingCollector);
    var metadataExtractor = AdfRendererFactory.contentMetadataExtractor();
    var parsingService = new AdfParsingService(mapper, astParser);
    var workflow =
        new AdfDocumentWorkflow(parsingService, headingCollector, renderer, metadataExtractor);
    return new AdfServices(mapper, astParser, parsingService, workflow);
  }

  public JsonMapper mapper() {
    return mapper;
  }

  public AdfAstParser astParser() {
    return astParser;
  }

  public AdfParsingService parsingService() {
    return parsingService;
  }

  public AdfDocumentWorkflow workflow() {
    return workflow;
  }
}
