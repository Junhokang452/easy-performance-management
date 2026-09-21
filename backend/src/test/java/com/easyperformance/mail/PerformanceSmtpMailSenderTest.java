package com.easyperformance.mail;

import com.easyperformance.domain.account.PerformanceUser;
import com.easyperformance.domain.account.PerformanceUserRepository;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PerformanceSmtpMailSenderTest {
    private final PerformanceUserRepository users = mock(PerformanceUserRepository.class);
    private final UUID tenant = UUID.randomUUID();
    private final UUID employee = UUID.randomUUID();

    @Test
    void remainsDisabledUntilExplicitlyConfigured() {
        var adapter = adapter(false, 587, "sender@example.test");
        assertThat(adapter.configured()).isFalse();
        assertThatThrownBy(() -> adapter.send(tenant, employee, "subject", "body"))
            .hasMessage("SMTP_CONFIGURATION_REQUIRED");
        verifyNoInteractions(users);
    }

    @Test
    void rejectsHeaderInjectionBeforeAnyDelivery() {
        var adapter = adapter(true, 587, "sender@example.test");
        assertThatThrownBy(() -> adapter.send(tenant, employee, "hello\r\nBcc: other@example.test", "body"))
            .hasMessage("MAIL_SUBJECT_INVALID");
        verifyNoInteractions(users);
        assertThat(adapter(true, 587, "sender@example.test\r\nBcc: other@example.test").configured()).isFalse();
    }

    @Test
    void missingOrInactiveTenantBoundRecipientCannotBeDelivered() {
        PerformanceUser inactive = user("inactive@example.test"); inactive.setActive(false);
        when(users.findAllByTenantIdAndEmployeeId(tenant, employee)).thenReturn(List.of(inactive));
        assertThatThrownBy(() -> adapter(true, 587, "sender@example.test").send(tenant, employee, "subject", "body"))
            .hasMessage("RECIPIENT_EMAIL_UNAVAILABLE");
        verify(users).findAllByTenantIdAndEmployeeId(tenant, employee);
    }

    @Test
    void ambiguousRecipientAddressesAreRejectedRatherThanChosenArbitrarily() {
        when(users.findAllByTenantIdAndEmployeeId(tenant, employee))
            .thenReturn(List.of(user("one@example.test"), user("two@example.test")));
        assertThatThrownBy(() -> adapter(true, 587, "sender@example.test").send(tenant, employee, "subject", "body"))
            .hasMessage("RECIPIENT_EMAIL_UNAVAILABLE");
    }

    @Test
    void configuredAdapterDeliversToLoopbackSmtpWithTenantBoundEnvelope() throws Exception {
        when(users.findAllByTenantIdAndEmployeeId(tenant, employee)).thenReturn(List.of(user("member@example.test")));
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             ExecutorService worker = Executors.newSingleThreadExecutor()) {
            server.setSoTimeout(10000);
            Future<List<String>> exchange = worker.submit(() -> receive(server));
            adapter(true, server.getLocalPort(), "sender@example.test").send(tenant, employee, "평가 시작 안내", "목표를 수립해 주세요.");
            List<String> lines = exchange.get(10, TimeUnit.SECONDS);
            assertThat(lines).anyMatch(s -> s.equalsIgnoreCase("MAIL FROM:<sender@example.test>"));
            assertThat(lines).anyMatch(s -> s.equalsIgnoreCase("RCPT TO:<member@example.test>"));
            assertThat(lines).anyMatch(s -> s.startsWith("Subject:"));
            assertThat(lines).anyMatch(s -> s.toLowerCase(Locale.ROOT).contains("charset=utf-8"));
            verify(users).findAllByTenantIdAndEmployeeId(tenant, employee);
        }
    }

    private PerformanceSmtpMailSender adapter(boolean enabled, int port, String from) {
        // Cleartext is deliberate only for this loopback SMTP fixture. Production defaults require TLS.
        return new PerformanceSmtpMailSender(users, enabled, "127.0.0.1", port, "", "", from, false, false);
    }

    private static PerformanceUser user(String email) {
        PerformanceUser user = new PerformanceUser(); user.setActive(true); user.setEmail(email); return user;
    }

    private static List<String> receive(ServerSocket server) throws IOException {
        try (Socket socket = server.accept()) {
            socket.setSoTimeout(10000);
            var input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            var output = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            List<String> lines = new ArrayList<>(); boolean data = false;
            output.print("220 local.test SMTP\r\n"); output.flush();
            for (String line; (line = input.readLine()) != null;) {
                lines.add(line);
                if (data) {
                    if (line.equals(".")) { data = false; output.print("250 Accepted\r\n"); }
                } else if (line.equalsIgnoreCase("DATA")) {
                    data = true; output.print("354 Send content\r\n");
                } else if (line.equalsIgnoreCase("QUIT")) {
                    output.print("221 Bye\r\n"); output.flush(); break;
                } else output.print("250 OK\r\n");
                output.flush();
            }
            return lines;
        }
    }
}
