package com.easyperformance.mail;

import com.easyperformance.domain.account.PerformanceUser;
import com.easyperformance.domain.account.PerformanceUserRepository;
import com.easyperformance.program.ProgramMailSender;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.util.Properties;
import java.util.UUID;

/** Explicitly configured SMTP adapter; queue and delivery status belong to the program outbox. */
@Component
public class PerformanceSmtpMailSender implements ProgramMailSender {
    private final PerformanceUserRepository users;
    private final JavaMailSenderImpl transport;
    private final boolean enabled;
    private final String from;

    public PerformanceSmtpMailSender(
        PerformanceUserRepository users,
        @Value("${performance.notifications.smtp.enabled:false}") boolean enabled,
        @Value("${performance.notifications.smtp.host:}") String host,
        @Value("${performance.notifications.smtp.port:587}") int port,
        @Value("${performance.notifications.smtp.username:}") String username,
        @Value("${performance.notifications.smtp.password:}") String password,
        @Value("${performance.notifications.smtp.from:}") String from,
        @Value("${performance.notifications.smtp.start-tls:true}") boolean startTls,
        @Value("${performance.notifications.smtp.ssl:false}") boolean ssl) {
        this.users = users;
        this.enabled = enabled;
        this.from = from.trim();
        this.transport = new JavaMailSenderImpl();
        transport.setHost(host.trim());
        transport.setPort(port);
        transport.setDefaultEncoding("UTF-8");
        if (!username.isBlank()) {
            transport.setUsername(username);
            transport.setPassword(password);
        }
        Properties settings = new Properties();
        settings.setProperty("mail.smtp.auth", Boolean.toString(!username.isBlank()));
        settings.setProperty("mail.smtp.starttls.enable", Boolean.toString(startTls && !ssl));
        settings.setProperty("mail.smtp.starttls.required", Boolean.toString(startTls && !ssl));
        settings.setProperty("mail.smtp.ssl.enable", Boolean.toString(ssl));
        settings.setProperty("mail.smtp.ssl.checkserveridentity", "true");
        settings.setProperty("mail.smtp.connectiontimeout", "5000");
        settings.setProperty("mail.smtp.timeout", "10000");
        settings.setProperty("mail.smtp.writetimeout", "10000");
        transport.setJavaMailProperties(settings);
    }

    @Override
    public boolean configured() {
        return enabled && transport.getHost() != null && !transport.getHost().isBlank()
            && transport.getPort() > 0 && transport.getPort() <= 65535 && validAddress(from);
    }

    @Override
    public void send(UUID tenantId, UUID recipientEmployeeId, String subject, String body) {
        if (!configured()) throw new IllegalStateException("SMTP_CONFIGURATION_REQUIRED");
        if (subject == null || subject.isBlank() || subject.contains("\r") || subject.contains("\n")) {
            throw new IllegalArgumentException("MAIL_SUBJECT_INVALID");
        }
        var recipients = users.findAllByTenantIdAndEmployeeId(tenantId, recipientEmployeeId).stream()
            .filter(PerformanceUser::isActive).map(PerformanceUser::getEmail).distinct().toList();
        if (recipients.size() != 1 || !validAddress(recipients.getFirst())) {
            throw new IllegalStateException("RECIPIENT_EMAIL_UNAVAILABLE");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipients.getFirst());
        message.setSubject(subject);
        message.setText(body == null ? "" : body);
        transport.send(message);
    }

    private static boolean validAddress(String value) {
        if (value == null || value.isBlank() || value.contains("\r") || value.contains("\n")) return false;
        try {
            InternetAddress[] addresses = InternetAddress.parse(value, true);
            if (addresses.length != 1 || addresses[0].isGroup()) return false;
            addresses[0].validate();
            return addresses[0].getAddress().contains("@");
        } catch (AddressException exception) {
            return false;
        }
    }
}
