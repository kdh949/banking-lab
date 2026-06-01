export function simulateKycCheck(customerId) {
  return {
    provider: "KYC-SIM",
    customerId,
    status: "VERIFIED",
    reference: `KYC-SIM-${customerId}`
  };
}

export function simulateNotification(message) {
  return {
    provider: "NOTIFICATION-SIM",
    status: "ACCEPTED",
    messageId: `MSG-${Date.now()}`,
    channel: message.channel || "SMS"
  };
}
