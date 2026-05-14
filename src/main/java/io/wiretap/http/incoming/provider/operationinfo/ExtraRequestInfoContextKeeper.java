package io.wiretap.http.incoming.provider.operationinfo;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ExtraRequestInfoContextKeeper {

    private static final ThreadLocal<String> EXTRA_INFO = new ThreadLocal<>();

    public static String getAndRemoveAdditionalInfo() {
        final String additionalInfo = EXTRA_INFO.get();
        EXTRA_INFO.remove();
        return additionalInfo;
    }

    public static void setAdditionalInfo(String info) {
        EXTRA_INFO.set(info);
    }

    public static void clear() {
        EXTRA_INFO.remove();
    }
}
