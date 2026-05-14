package io.wiretap.http.outgoing.interceptor.rest;

public class SourcePortLocalThreadKeeper {

    private static final ThreadLocal<Integer> SOURCE_PORT = new ThreadLocal<>();

    public static Integer getAndRemove() {
        final Integer sourcePort = SOURCE_PORT.get();
        SOURCE_PORT.remove();
        return sourcePort;
    }

    public static void set(int sourcePort) {
        SOURCE_PORT.set(sourcePort);
    }

    public static void clear() {
        SOURCE_PORT.remove();
    }
}
