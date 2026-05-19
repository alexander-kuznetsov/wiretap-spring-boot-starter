package io.wiretap.util;

import jakarta.xml.soap.MessageFactory;
import jakarta.xml.soap.MimeHeaders;
import jakarta.xml.soap.SOAPException;
import jakarta.xml.soap.SOAPMessage;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

class HeaderSelectorTest {

    // ---------- isWildcard ----------

    @Test
    void isWildcard_false_for_null_and_empty() {
        assertThat(HeaderSelector.isWildcard(null)).isFalse();
        assertThat(HeaderSelector.isWildcard(emptyList())).isFalse();
    }

    @Test
    void isWildcard_true_for_plain_asterisk() {
        assertThat(HeaderSelector.isWildcard(singletonList("*"))).isTrue();
    }

    @Test
    void isWildcard_true_when_asterisk_padded_with_whitespace() {
        assertThat(HeaderSelector.isWildcard(singletonList("  *  "))).isTrue();
    }

    @Test
    void isWildcard_true_when_mixed_with_explicit_names() {
        assertThat(HeaderSelector.isWildcard(asList("Content-Type", "*"))).isTrue();
    }

    @Test
    void isWildcard_false_for_regular_names() {
        assertThat(HeaderSelector.isWildcard(asList("Content-Type", "X-Forwarded-For"))).isFalse();
    }

    // ---------- select(Map<String,String>) ----------

    @Test
    void selectMap_filtersToConfiguredNames_inListOrder() {
        Map<String, String> all = new LinkedHashMap<>();
        all.put("Content-Type", "application/json");
        all.put("X-Tenant", "acme");
        all.put("X-Trace", "abc");

        Map<String, String> out = HeaderSelector.select(asList("X-Trace", "Content-Type"), all);

        assertThat(out).containsExactly(
                entry("X-Trace", "abc"),
                entry("Content-Type", "application/json"));
    }

    @Test
    void selectMap_returnsAll_inSourceOrder_whenWildcard() {
        Map<String, String> all = new LinkedHashMap<>();
        all.put("Content-Type", "application/json");
        all.put("X-Tenant", "acme");
        all.put("X-Trace", "abc");

        Map<String, String> out = HeaderSelector.select(singletonList("*"), all);

        assertThat(out).containsExactly(
                entry("Content-Type", "application/json"),
                entry("X-Tenant", "acme"),
                entry("X-Trace", "abc"));
    }

    @Test
    void selectMap_ignoresOtherElements_whenWildcardPresent() {
        Map<String, String> all = new LinkedHashMap<>();
        all.put("A", "1");
        all.put("B", "2");

        Map<String, String> out = HeaderSelector.select(asList("Z-Missing", "*", "B"), all);

        assertThat(out).containsKeys("A", "B");
    }

    @Test
    void selectMap_returnsEmpty_whenSourceEmpty_evenWithWildcard() {
        assertThat(HeaderSelector.select(singletonList("*"), new LinkedHashMap<>())).isEmpty();
        assertThat(HeaderSelector.select(singletonList("*"), (Map<String, String>) null)).isEmpty();
    }

    @Test
    void selectMap_skipsMissingHeaders_inExplicitMode() {
        Map<String, String> all = Map.of("A", "1");
        Map<String, String> out = HeaderSelector.select(asList("A", "B"), all);
        assertThat(out).containsOnlyKeys("A");
    }

    // ---------- select(HttpHeaders) ----------

    @Test
    void selectHttpHeaders_joinsMultiValueWithSemicolon() {
        HttpHeaders all = new HttpHeaders();
        all.add("X-Forwarded-For", "1.2.3.4");
        all.add("X-Forwarded-For", "5.6.7.8");
        all.add("Content-Type", "application/json");

        Map<String, String> out = HeaderSelector.select(singletonList("X-Forwarded-For"), all);

        assertThat(out).containsExactly(entry("X-Forwarded-For", "1.2.3.4;5.6.7.8"));
    }

    @Test
    void selectHttpHeaders_wildcardIncludesAll() {
        HttpHeaders all = new HttpHeaders();
        all.add("X-Tenant", "acme");
        all.add("X-Trace", "abc");

        Map<String, String> out = HeaderSelector.select(singletonList("*"), all);

        assertThat(out).containsKeys("X-Tenant", "X-Trace");
    }

    @Test
    void selectHttpHeaders_returnsEmptyMap_whenSourceNullOrEmpty() {
        assertThat(HeaderSelector.select(singletonList("*"), (HttpHeaders) null)).isEmpty();
        assertThat(HeaderSelector.select(singletonList("*"), new HttpHeaders())).isEmpty();
    }

    // ---------- selectMulti (Feign-style) ----------

    @Test
    void selectMulti_joinsMultiValueWithSemicolon() {
        Map<String, Collection<String>> all = new LinkedHashMap<>();
        all.put("X-Forwarded-For", asList("a", "b"));
        all.put("Content-Type", singletonList("application/json"));

        Map<String, String> out = HeaderSelector.selectMulti(asList("X-Forwarded-For", "Content-Type"), all);

        assertThat(out).containsExactly(
                entry("X-Forwarded-For", "a;b"),
                entry("Content-Type", "application/json"));
    }

    @Test
    void selectMulti_wildcardIncludesAll() {
        Map<String, Collection<String>> all = new LinkedHashMap<>();
        all.put("A", singletonList("1"));
        all.put("B", asList("2", "3"));

        Map<String, String> out = HeaderSelector.selectMulti(singletonList("*"), all);

        assertThat(out).containsExactly(entry("A", "1"), entry("B", "2;3"));
    }

    // ---------- selectMime (SOAP) ----------

    @Test
    void selectMime_explicit_joinsDuplicates() throws SOAPException {
        SOAPMessage msg = MessageFactory.newInstance().createMessage();
        MimeHeaders mh = msg.getMimeHeaders();
        mh.removeAllHeaders();
        mh.addHeader("X-Trace", "abc");
        mh.addHeader("X-Trace", "def");
        mh.addHeader("Content-Type", "text/xml");

        Map<String, String> out = HeaderSelector.selectMime(singletonList("X-Trace"), mh);

        assertThat(out).containsExactly(entry("X-Trace", "abc;def"));
    }

    @Test
    void selectMime_wildcardReturnsEveryHeader_joiningDuplicates() throws SOAPException {
        SOAPMessage msg = MessageFactory.newInstance().createMessage();
        MimeHeaders mh = msg.getMimeHeaders();
        mh.removeAllHeaders();
        mh.addHeader("X-Trace", "abc");
        mh.addHeader("X-Trace", "def");
        mh.addHeader("Content-Type", "text/xml");

        Map<String, String> out = HeaderSelector.selectMime(singletonList("*"), mh);

        assertThat(out).containsKeys("X-Trace", "Content-Type");
        assertThat(out.get("X-Trace")).isEqualTo("abc;def");
    }

    @Test
    void selectMime_nullSource_returnsEmptyMap() {
        assertThat(HeaderSelector.selectMime(singletonList("*"), null)).isEmpty();
    }

    // ---------- selectKafka ----------

    @Test
    void selectKafka_explicit_takesLastHeader() {
        Headers headers = new RecordHeaders();
        headers.add("x-trace-id", "v1".getBytes(StandardCharsets.UTF_8));
        headers.add("x-trace-id", "v2".getBytes(StandardCharsets.UTF_8));
        headers.add("x-request-id", "r1".getBytes(StandardCharsets.UTF_8));

        Map<String, String> out = HeaderSelector.selectKafka(asList("x-trace-id", "x-request-id"), headers);

        assertThat(out).containsExactly(
                entry("x-trace-id", "v2"),
                entry("x-request-id", "r1"));
    }

    @Test
    void selectKafka_wildcardReturnsEveryHeader_keepingInsertionOrder() {
        Headers headers = new RecordHeaders();
        headers.add("x-trace-id", "v1".getBytes(StandardCharsets.UTF_8));
        headers.add("x-request-id", "r1".getBytes(StandardCharsets.UTF_8));
        headers.add("x-tenant", "acme".getBytes(StandardCharsets.UTF_8));

        Map<String, String> out = HeaderSelector.selectKafka(singletonList("*"), headers);

        assertThat(out.keySet()).containsExactly("x-trace-id", "x-request-id", "x-tenant");
    }

    @Test
    void selectKafka_nullSource_returnsEmptyMap() {
        assertThat(HeaderSelector.selectKafka(singletonList("*"), null)).isEmpty();
    }

    @Test
    void selectKafka_emptyHeaders_withWildcard_returnsEmptyMap() {
        assertThat(HeaderSelector.selectKafka(singletonList("*"), new RecordHeaders())).isEmpty();
    }

    @Test
    void selectKafka_skipsNullValuedHeader() {
        Headers headers = new RecordHeaders();
        headers.add("x-trace-id", null);

        Map<String, String> out = HeaderSelector.selectKafka(singletonList("*"), headers);

        assertThat(out).isEmpty();
    }

    // ---------- helper ----------

    private static Map.Entry<String, String> entry(String key, String value) {
        return Map.entry(key, value);
    }

    @SuppressWarnings("unused")
    private static List<String> wildcardList() {
        return singletonList("*");
    }
}
