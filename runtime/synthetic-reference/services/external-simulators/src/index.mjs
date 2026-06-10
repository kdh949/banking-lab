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

function transferEntries(transactions, businessDate) {
  return transactions
    .filter((transaction) => transaction.status === "POSTED")
    .filter((transaction) => transaction.transactionType === "INTERNAL_TRANSFER")
    .filter((transaction) => !businessDate || transaction.businessDate === businessDate)
    .map((transaction) => ({
      referenceId: transaction.id,
      businessDate: transaction.businessDate,
      amountMinor: (transaction.postings || []).find((posting) => posting.direction === "DEBIT")?.amountMinor || 0,
      status: "SETTLED",
      provider: "OPENBANKING-SIM"
    }));
}

export function simulateExternalInstitutionFile(input = {}) {
  const entries = transferEntries(input.transactions || [], input.businessDate);
  if (input.mode === "MATCHED") {
    return {
      fileId: input.fileId || `EXT-FILE-${Date.now()}`,
      businessDate: input.businessDate,
      entries
    };
  }
  if (entries.length === 0) {
    return {
      fileId: input.fileId || `EXT-FILE-${Date.now()}`,
      businessDate: input.businessDate,
      entries: [
        {
          referenceId: `EXT-ONLY-${input.businessDate}`,
          businessDate: input.businessDate,
          amountMinor: 1000,
          status: "SETTLED",
          provider: "OPENBANKING-SIM"
        }
      ]
    };
  }
  return {
    fileId: input.fileId || `EXT-FILE-${Date.now()}`,
    businessDate: input.businessDate,
    entries: entries.map((entry, index) => index === 0
      ? {
        ...entry,
        amountMinor: entry.amountMinor + 1000,
        status: "SETTLED"
      }
      : entry)
  };
}
