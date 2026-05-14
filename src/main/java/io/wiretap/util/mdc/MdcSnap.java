package io.wiretap.util.mdc;

import org.slf4j.MDC;

import java.util.Map;

/**
 * Snapshot of the current thread's MDC that can be replayed on another thread.
 * <p>
 * When work is offloaded to a new thread (parallel streams, custom executors, etc.)
 * MDC is reset, so values populated by the inbound request handler are lost.
 * Capture an {@code MdcSnap} on the originating thread, then call
 * {@link #setMdc()} on the worker thread to restore the context.
 * <p>
 * See the README for usage examples.
 */
public class MdcSnap {

    private final Map<String, String> mdc;

    private MdcSnap() {
        this.mdc = MDC.getCopyOfContextMap();
    }

    public static MdcSnap getMdc() {
        return new MdcSnap();
    }

    public void setMdc() {
        MDC.clear();
        if (mdc != null) {
            MDC.setContextMap(mdc);
        }
    }
}
