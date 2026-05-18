package io.wiretap.http.message.settings.body;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Structural body-masking SPI. Operates on the parsed JSON tree, so an
 * implementation can mask specific fields on specific endpoints without
 * touching the rest of the payload — for example, mask
 * {@code remaining_auth} only on responses from {@code /api/cardlimits/.*}.
 *
 * <p>Register one or more Spring beans implementing this interface; on every
 * request/response body wiretap walks them in order and applies the first
 * one whose {@link #appliesTo} returns {@code true}. The simpler
 * {@link HttpBodyMaskingHandler} (recursive string-level masking) still runs
 * afterwards if it is registered — the two SPIs compose, structural maskers
 * are not a replacement.
 *
 * <p>The {@link JsonNode} passed in is owned by wiretap; implementations may
 * mutate it in place (e.g. via {@code ((ObjectNode) node).put(...)}).
 *
 * <pre>
 * &#64;Component
 * public class CardLimitsMasker implements HttpBodyMasker {
 *     private static final List&lt;String&gt; FIELDS = List.of("remaining_auth", "remaining_cash");
 *
 *     &#64;Override public boolean appliesTo(String url) {
 *         return url.contains("/api/cardlimits");
 *     }
 *
 *     &#64;Override public JsonNode mask(JsonNode body) {
 *         if (body.isObject()) {
 *             FIELDS.forEach(name -&gt; Optional.ofNullable(body.findValue(name))
 *                     .ifPresent(v -&gt; ((ObjectNode) body).put(name, "***")));
 *         }
 *         return body;
 *     }
 * }
 * </pre>
 */
public interface HttpBodyMasker {

    /**
     * @return {@code true} if {@link #mask} should be called for this request.
     *         The first masker returning {@code true} wins per body — subsequent
     *         maskers are not consulted.
     */
    boolean appliesTo(String requestUrl);

    /**
     * Apply masking to the parsed JSON tree. The argument is owned by wiretap,
     * so an implementation may mutate it in place or return a different node.
     */
    JsonNode mask(JsonNode body);
}
