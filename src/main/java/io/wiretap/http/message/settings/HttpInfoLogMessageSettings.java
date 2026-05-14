package io.wiretap.http.message.settings;

import lombok.Data;
import io.wiretap.http.message.settings.body.HttpBodySettings;
import io.wiretap.util.FieldVisibilityMap;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static io.wiretap.http.message.settings.HttpInfoLogMessageSettings.HttpConfigurableField.REQUEST_BODY;

@Data
public class HttpInfoLogMessageSettings {

    private HttpBodySettings httpBodySettings = new HttpBodySettings();

    /** Toggles masking applied to the logged request URL. */
    private boolean enableUrlMasking = true;

    /** Request headers logged by default. */
    private Collection<String> requestHeaders = Arrays.asList("Content-Type", "X-Forwarded-For");

    /** Response headers logged by default. */
    private Collection<String> responseHeaders = Collections.singletonList("Content-Type");

    private FieldVisibilityMap<HttpConfigurableField> visibilitySettings = getDefaultLogSettings();

    private List<SpecificHttpInfoLogMessageSettings> specificHttpInfoSettings = Collections.emptyList();

    public enum HttpConfigurableField {
        REQUEST_URL,
        REQUEST_HEADERS,
        REQUEST_PARAMS,
        REQUEST_BODY,
        RESPONSE_HEADERS,
        RESPONSE_BODY
    }

    private FieldVisibilityMap<HttpConfigurableField> getDefaultLogSettings() {
        final FieldVisibilityMap<HttpConfigurableField> defaultSettings = new FieldVisibilityMap<>(HttpConfigurableField.class);

        defaultSettings.put(HttpConfigurableField.REQUEST_URL, Boolean.TRUE);
        defaultSettings.put(HttpConfigurableField.REQUEST_HEADERS, Boolean.TRUE);
        defaultSettings.put(HttpConfigurableField.REQUEST_PARAMS, Boolean.TRUE);
        defaultSettings.put(REQUEST_BODY, Boolean.TRUE);
        defaultSettings.put(HttpConfigurableField.RESPONSE_HEADERS, Boolean.TRUE);
        defaultSettings.put(HttpConfigurableField.RESPONSE_BODY, Boolean.TRUE);

        return defaultSettings;
    }

    /**
     * Returns the effective settings for a given request URL.
     * <p>
     * The configuration has a "common" block that applies to every request and
     * an optional list of per-URL overrides. This method picks the first
     * matching override and merges it with the common settings; if no override
     * matches, the common settings are returned unchanged.
     *
     * @param requestUrl URL of the incoming request
     */
    public HttpInfoLogMessageSettings getRequestSettingsByUrl(String requestUrl) {
        // TODO add local caching for the resolved settings per URL
        return specificHttpInfoSettings.stream()
                .filter(settings -> requestUrl.matches(settings.getMatchUrlPattern()))
                .findFirst()
                .map(settings -> settings.getIntersectionSettings(this))
                .orElse(this);
    }
}
