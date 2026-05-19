package io.wiretap.util;

import jakarta.xml.soap.MimeHeader;
import jakarta.xml.soap.MimeHeaders;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.springframework.http.HttpHeaders;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves which headers to log against a configured list. The list may
 * contain the literal {@value #WILDCARD} to mean "log every header from
 * the source"; in that case the rest of the elements are ignored.
 *
 * <p>Used by every wiretap interceptor that publishes a header map into
 * the JSON log (incoming HTTP, RestTemplate, WebClient, Feign,
 * WebServiceTemplate, Kafka). The wildcard is intentionally not applied
 * to MDC-forwarding ({@code wiretap.headers.forward-to-mdc}) — that list
 * stays explicit by design.
 *
 * <p>Returned maps are always {@link LinkedHashMap}: wildcard mode keeps
 * the source iteration order, explicit mode keeps the order of the
 * configured list.
 */
public final class HeaderSelector {

    public static final String WILDCARD = "*";

    private HeaderSelector() {
    }

    public static boolean isWildcard(Collection<String> configured) {
        if (configured == null || configured.isEmpty()) {
            return false;
        }
        for (String s : configured) {
            if (s != null && WILDCARD.equals(s.trim())) {
                return true;
            }
        }
        return false;
    }

    /** Source: a pre-flattened {@code Map<String,String>} (servlet incoming, WebClient). */
    public static Map<String, String> select(Collection<String> configured, Map<String, String> all) {
        if (all == null || all.isEmpty()) {
            return new LinkedHashMap<>();
        }
        if (isWildcard(configured)) {
            return new LinkedHashMap<>(all);
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (String name : configured) {
            String value = all.get(name);
            if (value != null) {
                out.put(name, value);
            }
        }
        return out;
    }

    /** Source: Spring {@link HttpHeaders} (RestTemplate / RestClient / SOAP HTTP). */
    public static Map<String, String> select(Collection<String> configured, HttpHeaders all) {
        Map<String, String> out = new LinkedHashMap<>();
        if (all == null || all.isEmpty()) {
            return out;
        }
        if (isWildcard(configured)) {
            all.forEach((name, values) -> out.put(name, joinValues(values)));
            return out;
        }
        for (String name : configured) {
            List<String> values = all.get(name);
            if (values != null) {
                out.put(name, joinValues(values));
            }
        }
        return out;
    }

    /** Source: Feign {@code Map<String, Collection<String>>}. */
    public static Map<String, String> selectMulti(Collection<String> configured, Map<String, Collection<String>> all) {
        Map<String, String> out = new LinkedHashMap<>();
        if (all == null || all.isEmpty()) {
            return out;
        }
        if (isWildcard(configured)) {
            all.forEach((name, values) -> out.put(name, joinValues(values)));
            return out;
        }
        for (String name : configured) {
            Collection<String> values = all.get(name);
            if (values != null) {
                out.put(name, joinValues(values));
            }
        }
        return out;
    }

    /** Source: SOAP {@link MimeHeaders}. */
    public static Map<String, String> selectMime(Collection<String> configured, MimeHeaders all) {
        Map<String, String> out = new LinkedHashMap<>();
        if (all == null) {
            return out;
        }
        if (isWildcard(configured)) {
            Map<String, List<String>> grouped = new LinkedHashMap<>();
            Iterator<?> it = all.getAllHeaders();
            while (it.hasNext()) {
                MimeHeader header = (MimeHeader) it.next();
                grouped.computeIfAbsent(header.getName(), k -> new ArrayList<>()).add(header.getValue());
            }
            grouped.forEach((name, values) -> out.put(name, joinValues(values)));
            return out;
        }
        for (String name : configured) {
            String[] values = all.getHeader(name);
            if (values != null && values.length > 0) {
                out.put(name, String.join(";", values));
            }
        }
        return out;
    }

    /** Source: Kafka {@link Headers}. */
    public static Map<String, String> selectKafka(Collection<String> configured, Headers all) {
        Map<String, String> out = new LinkedHashMap<>();
        if (all == null) {
            return out;
        }
        if (isWildcard(configured)) {
            for (Header h : all) {
                if (h.value() != null) {
                    out.put(h.key(), new String(h.value(), StandardCharsets.UTF_8));
                }
            }
            return out;
        }
        if (configured == null) {
            return out;
        }
        for (String name : configured) {
            Header h = all.lastHeader(name);
            if (h != null && h.value() != null) {
                out.put(name, new String(h.value(), StandardCharsets.UTF_8));
            }
        }
        return out;
    }

    private static String joinValues(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return String.join(";", values.stream().map(v -> v == null ? "" : v).toList());
    }
}
