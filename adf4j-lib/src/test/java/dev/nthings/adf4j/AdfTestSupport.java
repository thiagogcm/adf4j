package dev.nthings.adf4j;

import java.io.IOException;

import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.ast.MacroParams;
import dev.nthings.adf4j.internal.parser.AdfAstParser;
import dev.nthings.adf4j.testing.TestResources;

import tools.jackson.databind.json.JsonMapper;

final class AdfTestSupport {

  private static final String CASE_ROOT = "adf/spec/";

  private final JsonMapper mapper;
  private final AdfAstParser astParser;
  private final AdfConverter processor;

  private AdfTestSupport(
      JsonMapper mapper,
      AdfAstParser astParser,
      AdfConverter processor) {
    this.mapper = mapper;
    this.astParser = astParser;
    this.processor = processor;
  }

  static AdfTestSupport create() {
    var mapper = JsonMapper.builder().build();
    return new AdfTestSupport(mapper, new AdfAstParser(mapper), new AdfConverter());
  }

  AdfConverter processor() {
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
