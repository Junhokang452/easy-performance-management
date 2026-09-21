package com.easyperformance.program;
import com.easyperformance.program.ProgramTypes.NotificationStatus;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;import java.time.Instant;import java.util.UUID;
@Service
public class ProgramNotificationStatusService{
 private final ProgramNotificationRepository notifications;public ProgramNotificationStatusService(ProgramNotificationRepository notifications){this.notifications=notifications;}
 @Transactional public boolean claim(UUID tenantId,UUID id){return notifications.claimReady(tenantId,id,NotificationStatus.READY,NotificationStatus.SENDING)==1;}
 @Transactional public void sent(UUID tenantId,UUID id){ProgramNotification n=notifications.findByIdAndTenantId(id,tenantId).orElseThrow();n.setStatus(NotificationStatus.SENT);n.setSentAt(Instant.now());n.setFailureReason(null);notifications.save(n);}
 @Transactional public void failed(UUID tenantId,UUID id,String reason){ProgramNotification n=notifications.findByIdAndTenantId(id,tenantId).orElseThrow();n.setStatus(NotificationStatus.FAILED);n.setFailureReason(reason==null?"DELIVERY_FAILED":reason.substring(0,Math.min(1000,reason.length())));notifications.save(n);}
}
