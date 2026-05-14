package io.wiretap.http.message.settings.body;

import lombok.Data;

@Data
public class HttpBodySettings {
    private boolean enableBodyMasking = false;
    private boolean enableBodyTruncating = false;
    private int maxFieldLength = 1000;
    private int maxBodyLength = 2000;
}
