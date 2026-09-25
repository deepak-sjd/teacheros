package com.teacheros.modules.notification;

public interface EmailSender {
    void send(String toAddress, String subject, String body);
}
