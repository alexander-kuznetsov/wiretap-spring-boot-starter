package io.wiretap.http.message.settings;

import lombok.Getter;
import lombok.Setter;
import org.springframework.util.CollectionUtils;

/**
 * Per-URL override of the common log settings.
 * Applied only when the request URL matches {@link #matchUrlPattern}.
 */
public class SpecificHttpInfoLogMessageSettings extends HttpInfoLogMessageSettings {

    @Getter
    @Setter
    private String matchUrlPattern;

    /**
     * Merges this override with the common settings: any field that was explicitly
     * customised here wins, anything left at its default value falls back to the
     * common settings.
     *
     * @param commonHttpInfoLogSettings common settings shared by all requests
     */
    public HttpInfoLogMessageSettings getIntersectionSettings(HttpInfoLogMessageSettings commonHttpInfoLogSettings) {
        final HttpInfoLogMessageSettings defaultSettings = new HttpInfoLogMessageSettings();
        final HttpInfoLogMessageSettings interSectionSettings = new HttpInfoLogMessageSettings();

        interSectionSettings.setHttpBodySettings(
                this.getHttpBodySettings().equals(defaultSettings.getHttpBodySettings())
                        ? commonHttpInfoLogSettings.getHttpBodySettings() : this.getHttpBodySettings()
        );

        interSectionSettings.setRequestHeaders(
                CollectionUtils.isEmpty(this.getRequestHeaders())
                        ? commonHttpInfoLogSettings.getRequestHeaders() : this.getRequestHeaders()
        );

        interSectionSettings.setResponseHeaders(
                CollectionUtils.isEmpty(this.getResponseHeaders())
                        ? commonHttpInfoLogSettings.getResponseHeaders() : this.getResponseHeaders()
        );

        interSectionSettings.setVisibilitySettings(
                this.getVisibilitySettings().equals(defaultSettings.getVisibilitySettings())
                        ? commonHttpInfoLogSettings.getVisibilitySettings() : this.getVisibilitySettings()
        );

        return interSectionSettings;
    }
}
