INSERT INTO notification_templates (
  template_id, event_type, channel, version, status, body_template, provider_kind, synthetic_only
)
VALUES (
  'NTPL-SMS-CUSTOMER-TRANSFER-STATUS-V1',
  'CustomerTransferStatusChanged',
  'SMS',
  1,
  'ACTIVE',
  'Synthetic transfer journey {journeyId} is now {status}.',
  'SYNTHETIC_SMS_SINK',
  true
);
