package com.teacheros.common.logging;

import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

public class MaskedThrowableConverter extends ThrowableProxyConverter {
    @Override
    public String convert(ILoggingEvent event) {
        return LogMasker.mask(super.convert(event));
    }
}
