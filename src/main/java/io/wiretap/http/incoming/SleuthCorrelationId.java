package io.wiretap.http.incoming;

import lombok.Getter;

public enum SleuthCorrelationId {

    SLEUTH_TRACE_ID("spring-sleuth-trace-id"),
    SLEUTH_SPAN_ID("spring-sleuth-span-id");

    @Getter
    private final String attributeName;

    SleuthCorrelationId(String attributeName) {
        this.attributeName = attributeName;
    }
}
