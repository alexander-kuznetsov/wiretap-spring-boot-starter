package io.wiretap.util.mdc;

import org.jetbrains.annotations.NotNull;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;
import java.util.Objects;

/**
 * Spring {@link TaskDecorator} that propagates MDC from the submitting thread into
 * the executing thread. Plug into a {@code ThreadPoolTaskExecutor} so logs emitted
 * inside {@code @Async} methods (or any executor-backed work) keep the correlation
 * IDs that were set on the request thread.
 * <p>
 * Mainly useful when Spring Cloud Sleuth / Micrometer Tracing is not configured
 * to do this propagation for you.
 * <p>
 * See the README for usage examples.
 */
public class MdcTaskDecorator implements TaskDecorator {

    @NotNull
    @Override
    public Runnable decorate(@NotNull Runnable runnable) {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return () -> {
            try {
                MDC.setContextMap(contextMap);
                Objects.requireNonNull(runnable).run();
            } finally {
                MDC.clear();
            }
        };
    }
}
