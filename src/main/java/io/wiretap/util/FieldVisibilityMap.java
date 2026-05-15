package io.wiretap.util;

import io.wiretap.http.outgoing.interceptor.Supplier;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

public class FieldVisibilityMap<K extends Enum<K>> extends EnumMap<K, Boolean> {

    public FieldVisibilityMap(final Class<K> keyType) {
        super(keyType);
    }

    public FieldVisibilityMap(final EnumMap<K, ? extends Boolean> m) {
        super(m);
    }

    public FieldVisibilityMap(final Map<K, ? extends Boolean> m) {
        super(m);
    }

    public <T> T getVisible(final K field, Supplier<T> param) throws IOException {
        return Boolean.TRUE.equals(this.get(field)) ? param.get() : null;
    }
}
