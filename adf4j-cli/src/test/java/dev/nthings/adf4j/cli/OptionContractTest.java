package dev.nthings.adf4j.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.nthings.adf4j.options.TableFallback;
import dev.nthings.adf4j.options.UnknownNodePolicy;
import java.util.Arrays;
import org.aesh.command.option.Option;
import org.junit.jupiter.api.Test;

/// Annotation values must be compile-time constants, so the `allowedValues` lists are literals
/// that can silently drift from their sources of truth. These tests pin them together: a new enum
/// constant in the library fails here until the annotation catches up.
class OptionContractTest {

  @Test
  void unknownNodesAllowedValuesMatchTheEnum() throws Exception {
    assertThat(allowedValues(RenderingOptions.class, "unknownNodes"))
        .containsExactlyInAnyOrder(kebabCased(UnknownNodePolicy.values()));
  }

  @Test
  void tableFallbackAllowedValuesMatchTheEnum() throws Exception {
    assertThat(allowedValues(RenderingOptions.class, "tableFallback"))
        .containsExactlyInAnyOrder(kebabCased(TableFallback.values()));
  }

  private static String[] allowedValues(Class<?> type, String field) throws Exception {
    return type.getDeclaredField(field).getAnnotation(Option.class).allowedValues();
  }

  private static String[] kebabCased(Enum<?>[] constants) {
    return Arrays.stream(constants).map(CliJson::kebabCase).toArray(String[]::new);
  }
}
