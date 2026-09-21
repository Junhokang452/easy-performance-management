package com.easyperformance.program;
import java.util.UUID;
/** Optional outbound adapter. Implementations must report configured=false until delivery is truly configured. */
public interface ProgramMailSender {boolean configured();void send(UUID tenantId,UUID recipientEmployeeId,String subject,String body);}
