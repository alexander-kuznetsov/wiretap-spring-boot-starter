package io.wiretap.util.mdc;

import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.Callable;

/**
 * {@link Callable} wrapper that captures the current thread's MDC at construction
 * time and restores it on the executing thread before invoking the delegate.
 * Useful when submitting work to an executor that does not propagate MDC by default.
 */
public class MdcCallableWrapper<T> implements Callable<T> {

    private final Callable<T> delegate;
    private final Map<String, String> mdcContext;

    public MdcCallableWrapper(Callable<T> delegate) {
        this.delegate = delegate;
        // capture the current MDC so it can be replayed on the worker thread
        this.mdcContext = MDC.getCopyOfContextMap();
    }

    @Override
    public T call() throws Exception {
        try {
            if (mdcContext != null) {
                MDC.setContextMap(mdcContext);
            }
            return delegate.call();
        } finally {
            MDC.clear();
        }
    }
}
