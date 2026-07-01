package com.chung.bot.log;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

public class DiscordAppender extends AppenderBase<ILoggingEvent> {
    @Override
    protected void append(ILoggingEvent eventObject) {
        // Đã tắt luồng OAuth2. Logic đăng nhập YouTube bằng thiết bị đã được loại bỏ.
    }
}
