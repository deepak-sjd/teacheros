package com.teacheros.common.logging;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

public class MaskedMessageConverter extends ClassicConverter {
    @Override
    public String convert(ILoggingEvent event) {
        return LogMasker.mask(event.getFormattedMessage());
    }
}
