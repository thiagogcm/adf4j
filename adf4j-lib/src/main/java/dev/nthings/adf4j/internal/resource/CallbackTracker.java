package dev.nthings.adf4j.internal.resource;

import dev.nthings.adf4j.result.Diagnostic;
import dev.nthings.adf4j.result.UnresolvedResource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/// Invokes caller callbacks and records their failures, independent of any renderer.
public final class CallbackTracker {

  private final List<Diagnostic> failures = new ArrayList<>();

  /// Runs `callback`, turning a thrown runtime exception into a `CALLBACK_FAILED` diagnostic.
  /// A failure returns `null`, so the caller falls through to its next source.
  public <T> @Nullable T call(String hook, String path, Supplier<@Nullable T> callback) {
    try {
      return callback.get();
    } catch (RuntimeException failure) {
      failures.add(
          new Diagnostic(
              Diagnostic.Code.CALLBACK_FAILED,
              hook + " failed: " + failure.getMessage(),
              failure,
              List.of(path),
              Map.of("callback", hook)));
      return null;
    }
  }

  public int failureCount() {
    return failures.size();
  }

  /// The reason for a still-missing dependency, given the failure count before it was sought.
  public UnresolvedResource.Reason reasonSince(int failuresBefore) {
    return failureCount() > failuresBefore
        ? UnresolvedResource.Reason.CALLBACK_FAILED
        : UnresolvedResource.Reason.NO_ANSWER;
  }

  public List<Diagnostic> diagnostics() {
    return List.copyOf(failures);
  }
}
