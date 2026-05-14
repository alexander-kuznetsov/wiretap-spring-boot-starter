package io.wiretap.http.outgoing.interceptor;

import java.io.IOException;

/**
 * Variant of {@link java.util.function.Supplier} that is allowed to throw
 * {@link IOException}. Used internally when wiring up I/O-bound visibility checks.
 */
@FunctionalInterface
public interface Supplier<T> {
    T get() throws IOException;
}
