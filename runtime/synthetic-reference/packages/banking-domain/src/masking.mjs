export function maskName(name) {
  if (!name) {
    return "";
  }
  if (name.length <= 2) {
    return `${name[0]}*`;
  }
  return `${name[0]}${"*".repeat(name.length - 2)}${name[name.length - 1]}`;
}

export function maskPhone(phone) {
  return String(phone || "").replace(/(\d{3})-\d{4}-(\d{4})/, "$1-****-$2");
}

export function maskAccountNo(accountNo) {
  const value = String(accountNo || "");
  const parts = value.split("-");
  if (parts.length >= 3) {
    return `${parts[0]}-${"*".repeat(parts[1].length)}-${parts[parts.length - 1].slice(-4)}`;
  }
  if (value.length <= 6) {
    return "****";
  }
  return `${value.slice(0, 4)}-${"*".repeat(Math.max(3, value.length - 10))}-${value.slice(-4)}`;
}

export function maskAddress(address) {
  if (!address) {
    return "";
  }
  const [first, second] = address.split(" ");
  return [first, second, "***"].filter(Boolean).join(" ");
}

export function maskCustomer(customer) {
  return {
    customerId: customer.customerId,
    maskedName: maskName(customer.name),
    maskedPhone: maskPhone(customer.phone),
    maskedAddress: maskAddress(customer.address),
    customerGrade: customer.customerGrade,
    riskGrade: customer.riskGrade
  };
}

export function maskAccount(account) {
  return {
    accountId: account.accountId,
    maskedAccountNo: maskAccountNo(account.accountNo),
    status: account.status,
    currency: account.currency
  };
}
