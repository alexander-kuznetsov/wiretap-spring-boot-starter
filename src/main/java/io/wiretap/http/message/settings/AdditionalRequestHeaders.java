package io.wiretap.http.message.settings;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class AdditionalRequestHeaders {
    private String matchUrlPattern;
    private List<String> additionalHeaderNames;
}
