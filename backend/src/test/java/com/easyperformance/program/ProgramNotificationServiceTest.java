package com.easyperformance.program;

import com.easyperformance.program.ProgramTypes.*;
import com.easyperformance.workflow.ActorAccess.Actor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProgramNotificationServiceTest {
    @Mock ProgramNotificationRepository notifications; @Mock com.easyperformance.readmodel.repository.RmEmployeeRepository employees;
    @Mock EvaluationProgramRepository programs; @Mock ProgramAccess access; @Mock ProgramMailSender mail;
    @Mock ProgramNotificationStatusService statuses;

    @Test
    void staleReadySnapshotFromTwoDispatchersStillSendsOnlyAfterOneAtomicClaim() {
        @SuppressWarnings("unchecked") ObjectProvider<ProgramMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mail); when(mail.configured()).thenReturn(true);
        ProgramNotificationService service = new ProgramNotificationService(notifications, employees, programs, access, provider, statuses);
        UUID tenantId = UUID.randomUUID(), programId = UUID.randomUUID(), notificationId = UUID.randomUUID();
        Actor operator = new Actor(UUID.randomUUID(), tenantId, UUID.randomUUID(), "operator", "HR_ADMIN");
        ProgramNotification notification = new ProgramNotification(); notification.setId(notificationId); notification.setTenantId(tenantId);
        notification.setProgramId(programId); notification.setRecipientEmployeeId(UUID.randomUUID()); notification.setChannel(NotificationChannel.EMAIL);
        notification.setStatus(NotificationStatus.READY); notification.setSubject("subject"); notification.setBody("body");
        when(notifications.findAllByTenantIdAndProgramIdAndChannelAndStatusOrderByCreatedAtAsc(
            tenantId, programId, NotificationChannel.EMAIL, NotificationStatus.READY)).thenReturn(List.of(notification));
        when(statuses.claim(tenantId, notificationId)).thenReturn(true, false);

        var first = service.dispatch(operator, programId);
        var second = service.dispatch(operator, programId);

        assertThat(first.sent()).isEqualTo(1); assertThat(second.sent()).isZero();
        verify(mail, times(1)).send(tenantId, notification.getRecipientEmployeeId(), "subject", "body");
        verify(statuses, times(1)).sent(tenantId, notificationId);
        verify(statuses, never()).failed(any(), any(), any());
    }
}
