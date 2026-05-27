package dev.nthings.adf4j;

import java.io.IOException;

import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.ast.MacroParams;
import dev.nthings.adf4j.parser.AdfAstParser;
import dev.nthings.adf4j.testing.TestResources;

import tools.jackson.databind.json.JsonMapper;

final class AdfTestSupport {

  private static final String CASE_ROOT = "adf/spec/";

  private final JsonMapper mapper;
  private final AdfAstParser astParser;
  private final AdfProcessor processor;

  private AdfTestSupport(
      JsonMapper mapper,
      AdfAstParser astParser,
      AdfProcessor processor) {
    this.mapper = mapper;
    this.astParser = astParser;
    this.processor = processor;
  }

  static AdfTestSupport create() {
    var services = AdfServices.createDefault();
    return new AdfTestSupport(
        services.mapper(),
        services.astParser(),
        new AdfProcessor(services));
  }

  AdfProcessor processor() {
    return processor;
  }

  String caseInput(String name) throws IOException {
    return TestResources.read(CASE_ROOT + name + ".json");
  }

  String caseOutput(String name, String suffix) throws IOException {
    return TestResources.read(CASE_ROOT + name + suffix).stripTrailing();
  }

  AdfDocument caseDocument(String name) throws IOException {
    return astParser.parseDocument(mapper.readTree(caseInput(name)));
  }

  MacroParams macroParams(String json) throws IOException {
    return astParser.parseMacroParams(mapper.readTree(json));
  }

  AdfDocument parseDocument(String json) throws IOException {
    return astParser.parseDocument(mapper.readTree(json));
  }
}
