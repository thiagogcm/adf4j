package dev.nthings.adf4j;

import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.ast.Attributes;
import dev.nthings.adf4j.ast.MacroParams;
import dev.nthings.adf4j.internal.parser.AdfAstParser;
import dev.nthings.adf4j.testing.TestResources;
import java.io.IOException;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

final class AdfTestSupport {

  private static final String CASE_ROOT = "adf/spec/";

  private final JsonMapper mapper;
  private final AdfAstParser astParser;
  private final AdfToMarkdown processor;

  private AdfTestSupport(JsonMapper mapper, AdfAstParser astParser, AdfToMarkdown processor) {
    this.mapper = mapper;
    this.astParser = astParser;
    this.processor = processor;
  }

  static AdfTestSupport create() {
    var mapper = JsonMapper.builder().build();
    return new AdfTestSupport(mapper, new AdfAstParser(mapper), AdfToMarkdown.create());
  }

  AdfToMarkdown processor() {
    return processor;
  }

  String caseInput(String name) throws IOException {
    return TestResources.read(CASE_ROOT + name + ".json");
  }

  String caseOutput(String name, String suffix) throws IOException {
    return TestResources.read(CASE_ROOT + name + suffix).stripTrailing();
  }

  AdfDocument caseDocument(String name) throws IOException {
    return astParser.parse(mapper.readTree(caseInput(name))).document();
  }

  MacroParams macroParams(String json) throws IOException {
    return MacroParams.from(new Attributes(mapper.convertValue(mapper.readTree(json), Map.class)));
  }

  AdfDocument parseDocument(String json) throws IOException {
    return astParser.parse(mapper.readTree(json)).document();
  }
}
