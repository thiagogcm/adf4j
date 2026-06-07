package dev.nthings.adf4j.internal.render;

import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs a caller-supplied callback; a thrown {@link RuntimeException} is logged and the fallback
 * returned, so one buggy resolver/extension cannot abort the whole conversion.
 */
final class CallbackGuards {

  private static final Logger log = LoggerFactory.getLogger(CallbackGuards.class);

  private CallbackGuards() {
  }

  static <T> T guard(String callbackName, Supplier<T> call, T fallback) {
    try {
      return call.get();
    } catch (RuntimeException exception) {
      log.warn("Caller {} threw; falling back to the default", callbackName, exception);
      return fallback;
    }
  }
}
