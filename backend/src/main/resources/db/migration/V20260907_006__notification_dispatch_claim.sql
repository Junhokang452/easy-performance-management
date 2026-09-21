ALTER TABLE program_notification DROP CONSTRAINT ck_program_notification_status;
ALTER TABLE program_notification ADD CONSTRAINT ck_program_notification_status
    CHECK (status IN ('READY','SENDING','CONFIG_REQUIRED','SENT','FAILED','READ'));
