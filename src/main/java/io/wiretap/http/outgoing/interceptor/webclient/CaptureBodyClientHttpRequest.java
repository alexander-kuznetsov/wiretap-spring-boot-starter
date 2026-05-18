package io.wiretap.http.outgoing.interceptor.webclient;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Decorator over {@link ClientHttpRequest} that intercepts {@code writeWith()}
 * to capture the request body bytes without consuming them.
 * <p>
 * Uses a peek-then-reset pattern: reads each buffer up to the {@code maxCapture}
 * cap, resets the read position, then passes the unchanged buffer to the
 * delegate. Once the cap is reached, further bytes are not stored — but the
 * downstream buffers are still forwarded as-is, so the wire payload is never
 * truncated. Memory cost is bounded to {@code maxCapture} bytes regardless of
 * payload size.
 */
class CaptureBodyClientHttpRequest implements ClientHttpRequest {

    static final String TRUNCATED_MARKER = "...[truncated]";

    private final ClientHttpRequest delegate;
    private final int maxCapture;
    private final AtomicReference<String> capturedBody = new AtomicReference<>("");

    CaptureBodyClientHttpRequest(ClientHttpRequest delegate, int maxCapture) {
        this.delegate = delegate;
        this.maxCapture = Math.max(0, maxCapture);
    }

    @Override
    public Mono<Void> writeWith(org.reactivestreams.Publisher<? extends DataBuffer> body) {
        List<byte[]> chunks = new ArrayList<>();
        int[] captured = {0};
        boolean[] truncated = {false};
        Flux<? extends DataBuffer> tapped = Flux.from(body)
                .doOnNext(buffer -> {
                    int avail = buffer.readableByteCount();
                    if (avail == 0) return;
                    int remaining = maxCapture - captured[0];
                    if (remaining <= 0) {
                        truncated[0] = true;
                        return;
                    }
                    int toRead = Math.min(remaining, avail);
                    int pos = buffer.readPosition();
                    byte[] bytes = new byte[toRead];
                    buffer.read(bytes);
                    buffer.readPosition(pos);
                    chunks.add(bytes);
                    captured[0] += toRead;
                    if (toRead < avail) truncated[0] = true;
                })
                .doOnComplete(() -> capturedBody.set(assembleChunks(chunks, truncated[0])));
        return delegate.writeWith(tapped);
    }

    @Override
    public Mono<Void> writeAndFlushWith(org.reactivestreams.Publisher<? extends org.reactivestreams.Publisher<? extends DataBuffer>> body) {
        return delegate.writeAndFlushWith(body);
    }

    @Override
    public Mono<Void> setComplete() {
        return delegate.setComplete();
    }

    @Override
    public HttpHeaders getHeaders() {
        return delegate.getHeaders();
    }

    @Override
    public DataBufferFactory bufferFactory() {
        return delegate.bufferFactory();
    }

    @Override
    public void beforeCommit(Supplier<? extends Mono<Void>> action) {
        delegate.beforeCommit(action);
    }

    @Override
    public boolean isCommitted() {
        return delegate.isCommitted();
    }

    @Override
    public HttpMethod getMethod() {
        return delegate.getMethod();
    }

    @Override
    public URI getURI() {
        return delegate.getURI();
    }

    @Override
    public MultiValueMap<String, HttpCookie> getCookies() {
        return delegate.getCookies();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getNativeRequest() {
        return delegate.getNativeRequest();
    }

    // Implemented without @Override on purpose: Spring 6.2+ (Spring Boot 3.4+)
    // promoted ClientHttpRequest#getAttributes() to an abstract method, while
    // earlier versions don't declare it at all. This signature compiles on both,
    // satisfies the abstract method on 6.2+, and is harmless on older versions.
    public Map<String, Object> getAttributes() {
        return Collections.emptyMap();
    }

    public String getCapturedBody() {
        return capturedBody.get();
    }

    private static String assembleChunks(List<byte[]> chunks, boolean truncated) {
        int total = chunks.stream().mapToInt(c -> c.length).sum();
        byte[] all = new byte[total];
        int offset = 0;
        for (byte[] chunk : chunks) {
            System.arraycopy(chunk, 0, all, offset, chunk.length);
            offset += chunk.length;
        }
        String body = new String(all, StandardCharsets.UTF_8);
        return truncated ? body + TRUNCATED_MARKER : body;
    }
}
