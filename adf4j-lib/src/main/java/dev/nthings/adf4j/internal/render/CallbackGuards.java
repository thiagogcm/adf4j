package dev.nthings.adf4j.internal.render;

import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// Runs a caller-supplied callback; a thrown {@link RuntimeException} is logged and yields `null`,
/// so one buggy resolver/extension cannot abort the whole conversion.
final class CallbackGuards {

  private static final Logger log = LoggerFactory.getLogger(CallbackGuards.class);

  private CallbackGuards() {}

  static <T> @Nullable T guard(String callbackName, Supplier<? extends @Nullable T> call) {
    try {
      return call.get();
    } catch (RuntimeException exception) {
      log.warn("Caller {} threw; falling back to the default", callbackName, exception);
      return null;
    }
  }

  /// Runs a string-valued resolver under {@link #guard} and applies the shared decline rule: a null
  /// or blank return (or a throw) means "declined" and comes back as `null`.
  static @Nullable String guardNonBlank(String callbackName, Supplier<@Nullable String> call) {
    var resolved = guard(callbackName, call);
    return resolved == null || resolved.isBlank() ? null : resolved;
  }
}
