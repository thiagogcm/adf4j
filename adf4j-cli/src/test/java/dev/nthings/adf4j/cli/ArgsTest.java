package dev.nthings.adf4j.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class ArgsTest {

  private static Args.Spec spec() {
    return new Args.Spec()
        .flag("collapse", "-c", "--collapse")
        .flag("escape", "-p", "--escape")
        .value("output", "-o", "--output")
        .value("title", "-t", "--title")
        .value("select", "--select");
  }

  @Test
  void clusteredShortBooleanFlags() {
    var args = Args.parse(List.of("-cp"), spec());
    assertThat(args.has("collapse")).isTrue();
    assertThat(args.has("escape")).isTrue();
  }

  @Test
  void shortValueAttachedAndSeparated() {
    assertThat(Args.parse(List.of("-oout.md"), spec()).value("output")).isEqualTo("out.md");
    assertThat(Args.parse(List.of("-o", "out.md"), spec()).value("output")).isEqualTo("out.md");
  }

  @Test
  void longValueInlineAndSeparated() {
    assertThat(Args.parse(List.of("--title=Hi"), spec()).value("title")).isEqualTo("Hi");
    assertThat(Args.parse(List.of("--title", "Hi"), spec()).value("title")).isEqualTo("Hi");
  }

  @Test
  void repeatedOptionCollectsAllValues() {
    var args = Args.parse(List.of("--select", "a", "--select", "b"), spec());
    assertThat(args.values("select")).containsExactly("a", "b");
    assertThat(args.value("select")).isEqualTo("b"); // last wins for single-value access
  }

  @Test
  void doubleDashEndsOptionParsing() {
    var args = Args.parse(List.of("--", "-o", "file"), spec());
    assertThat(args.positionals()).containsExactly("-o", "file");
    assertThat(args.value("output")).isNull();
  }

  @Test
  void positionalsAndStdinDashAreCollected() {
    var args = Args.parse(List.of("-c", "a.json", "-"), spec());
    assertThat(args.has("collapse")).isTrue();
    assertThat(args.positionals()).containsExactly("a.json", "-");
  }

  @Test
  void unknownOptionThrowsUsage() {
    assertThatThrownBy(() -> Args.parse(List.of("--nope"), spec()))
        .isInstanceOf(CliException.class)
        .hasMessageContaining("unknown option '--nope'");
  }

  @Test
  void missingValueThrowsUsage() {
    assertThatThrownBy(() -> Args.parse(List.of("--title"), spec()))
        .isInstanceOf(CliException.class)
        .hasMessageContaining("requires a value");
  }
}
