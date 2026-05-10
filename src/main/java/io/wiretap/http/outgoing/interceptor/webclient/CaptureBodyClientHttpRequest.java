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
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Decorator over {@link ClientHttpRequest} that intercepts {@code writeWith()}
 * to capture the request body bytes without consuming them.
 * Uses a peek-then-reset pattern: reads each buffer, saves the bytes,
 * resets the read position, then passes the unchanged buffer to the delegate.
 */
class CaptureBodyClientHttpRequest implements ClientHttpRequest {

    private final ClientHttpRequest delegate;
    private final AtomicReference<String> capturedBody = new AtomicReference<>("");

    CaptureBodyClientHttpRequest(ClientHttpRequest delegate) {
        this.delegate = delegate;
    }

    @Override
    public Mono<Void> writeWith(org.reactivestreams.Publisher<? extends DataBuffer> body) {
        List<byte[]> chunks = new ArrayList<>();
        Flux<? extends DataBuffer> tapped = Flux.from(body)
                .doOnNext(buffer -> {
                    int pos = buffer.readPosition();
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    buffer.readPosition(pos);
                    chunks.add(bytes);
                })
                .doOnComplete(() -> capturedBody.set(assembleChunks(chunks)));
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

    public String getCapturedBody() {
        return capturedBody.get();
    }

    private static String assembleChunks(List<byte[]> chunks) {
        int total = chunks.stream().mapToInt(c -> c.length).sum();
        byte[] all = new byte[total];
        int offset = 0;
        for (byte[] chunk : chunks) {
            System.arraycopy(chunk, 0, all, offset, chunk.length);
            offset += chunk.length;
        }
        return new String(all, StandardCharsets.UTF_8);
    }
}
