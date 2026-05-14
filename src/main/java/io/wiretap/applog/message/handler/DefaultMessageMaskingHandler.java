package io.wiretap.applog.message.handler;

import io.wiretap.util.MaskUtil;

public class DefaultMessageMaskingHandler implements MessageMaskingHandler {

    @Override
    public String maskMessage(String message) {
        return MaskUtil.maskLog(message);
    }
}
