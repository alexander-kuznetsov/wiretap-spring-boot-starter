package io.wiretap.http.incoming.encoder.postprocessor;

public interface HttpAccessEventPostProcessHandler {

    /**
     * Receives the fully encoded access-log entry just before it is written out,
     * giving the application a hook for post-processing (e.g. publishing custom
     * metrics derived from the log payload).
     * <p>
     * To plug in custom logic, register a Spring bean implementing this interface.
     * It is wired into the encoder during configuration startup.
     *
     * @param encodedBytes the fully encoded log message ready to be written
     */
    void performPostProcess(final byte[] encodedBytes);
}
