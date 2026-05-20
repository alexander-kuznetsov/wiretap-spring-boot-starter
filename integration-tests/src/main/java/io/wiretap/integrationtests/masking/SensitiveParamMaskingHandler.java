package io.wiretap.integrationtests.masking;

import io.wiretap.http.message.HttpRequestParamsMaskingHandler;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

@Component
public class SensitiveParamMaskingHandler implements HttpRequestParamsMaskingHandler {

    private static final Set<String> SENSITIVE = Set.of("phone", "passport", "token", "password");

    @Override
    public String maskParamValue(String name, String value) {
        if (name == null || value == null) {
            return value;
        }
        return SENSITIVE.contains(name.toLowerCase(Locale.ROOT)) ? "***" : value;
    }
}
