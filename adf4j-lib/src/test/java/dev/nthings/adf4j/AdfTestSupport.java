package dev.nthings.adf4j;

import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.ast.Attributes;
import dev.nthings.adf4j.ast.MacroParams;
import dev.nthings.adf4j.testing.TestResources;
import java.io.IOException;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

final class AdfTestSupport {

  private static final String CASE_ROOT = "adf/spec/";

  private final JsonMapper mapper;
  private final AdfToMarkdown processor;

  private AdfTestSupport(JsonMapper mapper, AdfToMarkdown processor) {
    this.mapper = mapper;
    this.processor = processor;
  }

  static AdfTestSupport create() {
    return new AdfTestSupport(JsonMapper.builder().build(), AdfToMarkdown.create());
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
    return parseDocument(caseInput(name));
  }

  MacroParams macroParams(String json) throws IOException {
    return MacroParams.from(attributes(json));
  }

  Attributes attributes(String json) throws IOException {
    return new Attributes(mapper.convertValue(mapper.readTree(json), Map.class));
  }

  AdfDocument parseDocument(String json) {
    return AdfJson.parse(json).document();
  }
}
