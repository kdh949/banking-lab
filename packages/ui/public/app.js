(function () {
  const appName = window.__LAB_APP__ || "customer-web";
  const root = document.getElementById("app");

  const appTitles = {
    "customer-web": "Customer Web Banking",
    "staff-terminal": "Staff Integrated Terminal",
    "complaint-portal": "Electronic Complaint Portal",
    "ops-console": "Operations Console",
    "audit-console": "Audit Console",
    "fds-aml-console": "FDS AML Console"
  };

  function money(minor) {
    return new Intl.NumberFormat("en-US", {
      style: "currency",
      currency: "KRW",
      maximumFractionDigits: 0
    }).format(Number(minor || 0));
  }

  async function api(path, options) {
    const response = await fetch(path, {
      headers: { "content-type": "application/json" },
      ...options
    });
    const payload = await response.json();
    if (!response.ok) {
      throw new Error(payload.error || response.statusText);
    }
    return payload;
  }

  function shell(content, rightPanel) {
    root.innerHTML = `
      <main class="app-shell">
        <header class="topbar">
          <strong>${appTitles[appName]}</strong>
          <div class="session-strip">
            <span>Mock auth</span>
            <span>Branch LAB-001</span>
            <span class="badge ok">Synthetic only</span>
          </div>
        </header>
        ${content(rightPanel)}
      </main>
    `;
  }

  function nav(items, active) {
    return `
      <nav class="rail">
        ${items.map((item) => `<a class="nav-item ${item === active ? "active" : ""}" href="#">${item}<span>&rsaquo;</span></a>`).join("")}
      </nav>
    `;
  }

  async function renderStaffTerminal() {
    const screens = await api("/api/screens?app=staff-terminal");
    shell(() => `
      <section class="workspace">
        ${nav(["Customer", "Account", "Transfer", "Complaint", "AML/FDS", "Reconciliation", "Approval", "Audit"], "Customer")}
        <section class="panel">
          <div class="panel-header">
            <h1>Business Work Area</h1>
            <span class="badge audit">Manifest driven</span>
          </div>
          <div class="panel-body">
            <div class="terminal-grid">
              <label>Transaction code
                <input id="tx-code" value="CST-001" aria-label="Transaction code">
              </label>
              <label>Lookup reason
                <input id="lookup-reason" value="Customer requested branch support" aria-label="Lookup reason">
              </label>
              <button id="run-code">Run</button>
            </div>
            <div class="form-grid" style="margin-top:10px">
              <label>Customer ID
                <input id="staff-customer-id" value="SYN-CUS-001" aria-label="Customer ID">
              </label>
              <label>Account ID
                <input id="staff-account-id" value="ACC-SYN-001-001" aria-label="Account ID">
              </label>
              <label>New phone
                <input id="staff-new-phone" value="010-0000-1999" aria-label="New phone">
              </label>
              <label>New address
                <input id="staff-new-address" value="Seoul Synthetic Updated" aria-label="New address">
              </label>
            </div>
            <div class="toolbar">
              <button class="secondary" data-code="CST-001">CST-001</button>
              <button class="secondary" data-code="CST-002">CST-002</button>
              <button class="secondary" data-code="ACC-101">ACC-101</button>
              <button class="secondary" data-code="LED-101">LED-101</button>
              <button class="secondary" data-code="CST-103">CST-103</button>
              <button class="secondary" data-code="CMP-201">CMP-201</button>
              <button class="secondary" data-code="APR-001">APR-001</button>
              <button class="secondary" data-code="AUD-001">AUD-001</button>
            </div>
          </div>
          <div class="tabs">
            ${screens.items.map((screen, index) => `<div class="tab ${index === 0 ? "active" : ""}">${screen.screenId} ${screen.title}</div>`).join("")}
          </div>
          <div class="panel-body business-screen">
            <div class="mini-card">
              <h3 id="work-title">CST-001 Customer Integrated Search</h3>
              <div class="kv"><span>Status</span><span id="work-status">Ready</span></div>
            </div>
            <table class="data-table">
              <thead><tr><th>Customer ID</th><th>Name</th><th>Phone</th><th>Risk</th><th>Audit</th></tr></thead>
              <tbody id="customer-results"><tr><td colspan="5">No customer result</td></tr></tbody>
            </table>
            <table class="data-table">
              <thead><tr><th>Account</th><th>Status</th><th>Ledger</th><th>Available</th><th>Audit</th></tr></thead>
              <tbody id="account-results"><tr><td colspan="5">No account result</td></tr></tbody>
            </table>
            <table class="data-table">
              <thead><tr><th>Transaction</th><th>Type</th><th>Status</th><th>Posting</th><th>Audit</th></tr></thead>
              <tbody id="transaction-results"><tr><td colspan="5">No transaction result</td></tr></tbody>
            </table>
            <table class="data-table">
              <thead><tr><th>Case</th><th>Status</th><th>Owner</th><th>SLA</th><th>Answer</th></tr></thead>
              <tbody id="complaint-results"><tr><td colspan="5">No complaint result</td></tr></tbody>
            </table>
          </div>
        </section>
        <aside class="side-panel">
          <div class="mini-card">
            <h3>Customer Context</h3>
            <div class="stack" id="customer-context">
              <div class="kv"><span>Customer</span><strong>Not selected</strong></div>
              <div class="kv"><span>PII</span><span>Masked by default</span></div>
            </div>
          </div>
          <div class="mini-card">
            <h3>Approval Inbox</h3>
            <div class="stack" id="approval-inbox">Loading...</div>
          </div>
          <div class="mini-card">
            <h3>Audit Log</h3>
            <div class="stack" id="audit-log">Loading...</div>
          </div>
        </aside>
      </section>
    `);

    function value(id) {
      return document.getElementById(id).value.trim();
    }

    function setStatus(message) {
      document.getElementById("work-status").textContent = message;
    }

    async function refreshSidePanels() {
      const [approvals, audits] = await Promise.all([
        api("/api/staff/approvals"),
        api("/api/staff/audit-events")
      ]);
      document.getElementById("approval-inbox").innerHTML = approvals.items.length
        ? approvals.items.slice(-5).reverse().map((item) => `<div class="kv"><span>${item.businessType}</span><span>${item.approvalId} ${item.status}</span></div>`).join("")
        : `<span class="badge ok">No pending approvals</span>`;
      document.getElementById("audit-log").innerHTML = audits.items.slice(-5).reverse().map((item) => `
        <div class="kv"><span>${item.eventType}</span><span>${item.reason || "system"}</span></div>
      `).join("") || `<span>No audit events yet</span>`;
      return { approvals, audits };
    }

    function renderCustomerRows(items, auditEventId) {
      document.getElementById("customer-results").innerHTML = items.map((customer) => `
        <tr>
          <td>${customer.customerId}</td>
          <td>${customer.maskedName || customer.name}</td>
          <td>${customer.maskedPhone || customer.phone}</td>
          <td>${customer.riskGrade}</td>
          <td><span class="badge audit">${auditEventId}</span></td>
        </tr>
      `).join("");
    }

    function renderAccountRows(items, auditEventId) {
      document.getElementById("account-results").innerHTML = items.map((account) => `
        <tr>
          <td>${account.maskedAccountNo}</td>
          <td><span class="badge ok">${account.status}</span></td>
          <td>${money(account.ledgerBalanceMinor)}</td>
          <td>${money(account.availableBalanceMinor)}</td>
          <td><span class="badge audit">${auditEventId}</span></td>
        </tr>
      `).join("") || `<tr><td colspan="5">No account result</td></tr>`;
    }

    function renderTransactionRows(items, auditEventId) {
      document.getElementById("transaction-results").innerHTML = items.map((transaction) => {
        const posting = transaction.postings[0] || {};
        return `
          <tr>
            <td>${transaction.transactionId}</td>
            <td>${transaction.transactionType}</td>
            <td><span class="badge ok">${transaction.status}</span></td>
            <td>${posting.direction || ""} ${money(posting.amountMinor || 0)}</td>
            <td><span class="badge audit">${auditEventId}</span></td>
          </tr>
        `;
      }).join("") || `<tr><td colspan="5">No transaction result</td></tr>`;
    }

    function renderComplaintRows(items) {
      document.getElementById("complaint-results").innerHTML = items.map((item) => `
        <tr>
          <td>${item.caseId}</td>
          <td><span class="badge warn">${item.status}</span></td>
          <td>${item.owner || ""}</td>
          <td>${item.slaDueAt || `${item.slaHours}h`}</td>
          <td>${item.answer?.body || item.answerDraft?.body || ""}</td>
        </tr>
      `).join("") || `<tr><td colspan="5">No complaint result</td></tr>`;
    }

    async function runComplaintWorkflow() {
      const list = await api("/api/staff/complaints");
      let target = list.items.find((item) => item.status !== "CLOSED") || list.items[0];
      if (!target) {
        setStatus("No complaint case");
        return;
      }
      if (target.status === "RECEIVED") {
        const result = await api(`/api/staff/complaints/${target.caseId}/classify`, {
          method: "POST",
          body: JSON.stringify({
            actorId: "complaint01",
            category: target.category,
            classification: target.category,
            note: "Staff terminal classification"
          })
        });
        target = result.item;
      }
      if (target.status === "CLASSIFIED") {
        const result = await api(`/api/staff/complaints/${target.caseId}/assign`, {
          method: "POST",
          body: JSON.stringify({
            actorId: "complaint01",
            owner: "complaint01"
          })
        });
        target = result.item;
      }
      if (target.status === "ASSIGNED") {
        const result = await api(`/api/staff/complaints/${target.caseId}/start-review`, {
          method: "POST",
          body: JSON.stringify({
            actorId: "complaint01",
            note: "Staff terminal review"
          })
        });
        target = result.item;
      }
      if (target.status === "IN_REVIEW") {
        const result = await api(`/api/staff/complaints/${target.caseId}/answer-drafts`, {
          method: "POST",
          body: JSON.stringify({
            actorId: "complaint01",
            requestedByRole: "COMPLAINT_HANDLER",
            reason: "Draft complaint answer for customer response",
            body: "Synthetic answer draft approved for customer response."
          })
        });
        target = result.item;
      }
      if (target.status === "WAITING_APPROVAL" && target.approvalId) {
        const result = await api(`/api/staff/approvals/${target.approvalId}/approve`, {
          method: "POST",
          body: JSON.stringify({
            approvedBy: "manager01",
            approvedByRole: "BRANCH_MANAGER"
          })
        });
        target = result.complaint || target;
      }
      renderComplaintRows([target]);
    }

    async function runCode() {
      const code = value("tx-code").toUpperCase();
      const reason = encodeURIComponent(value("lookup-reason"));
      const customerId = encodeURIComponent(value("staff-customer-id"));
      const accountId = encodeURIComponent(value("staff-account-id"));
      document.getElementById("work-title").textContent = `${code} ${screens.items.find((screen) => screen.screenId === code)?.title || "Staff Operation"}`;
      setStatus("Running");
      if (code === "CST-001") {
        const result = await api(`/api/staff/customers/search?query=${customerId}&reason=${reason}`);
        renderCustomerRows(result.items, result.auditEventId);
        const selected = result.items.find((item) => item.customerId === decodeURIComponent(customerId)) || result.items[0];
        document.getElementById("staff-customer-id").value = selected.customerId;
        document.getElementById("customer-context").innerHTML = `
          <div class="kv"><span>Customer</span><strong>${selected.maskedName}</strong></div>
          <div class="kv"><span>Customer ID</span><span>${selected.customerId}</span></div>
          <div class="kv"><span>Reason</span><span>${decodeURIComponent(reason)}</span></div>
        `;
      } else if (code === "CST-002") {
        const result = await api(`/api/staff/customers/${customerId}/detail?reason=${reason}`);
        renderCustomerRows([result.item], result.auditEventId);
      } else if (code === "ACC-101") {
        const result = await api(`/api/staff/accounts/search?customerId=${customerId}&accountId=${accountId}&reason=${reason}`);
        renderAccountRows(result.items, result.auditEventId);
      } else if (code === "LED-101") {
        const result = await api(`/api/staff/transactions/search?accountId=${accountId}&reason=${reason}`);
        renderTransactionRows(result.items, result.auditEventId);
      } else if (code === "CST-103") {
        const result = await api(`/api/staff/customers/${customerId}/change-requests`, {
          method: "POST",
          body: JSON.stringify({
            requestedBy: "branch01",
            reason: decodeURIComponent(reason),
            afterSnapshot: {
              phone: value("staff-new-phone"),
              address: value("staff-new-address")
            }
          })
        });
        renderCustomerRows([result.customer], result.item.auditEventId);
      } else if (code === "APR-001") {
        const panels = await refreshSidePanels();
        const pending = panels.approvals.items.find((item) => item.status === "PENDING" && item.businessType === "CUSTOMER_INFO_CHANGE");
        if (pending) {
          const result = await api(`/api/staff/approvals/${pending.approvalId}/approve`, {
            method: "POST",
            body: JSON.stringify({
              approvedBy: "manager01",
              approvedByRole: "BRANCH_MANAGER"
            })
          });
          if (result.customer) {
            renderCustomerRows([result.customer], result.item.auditEventId);
          }
        }
      } else if (code === "CMP-201") {
        await runComplaintWorkflow();
      } else if (code === "AUD-001") {
        await refreshSidePanels();
      }
      await refreshSidePanels();
      setStatus("Done");
    }

    document.querySelectorAll("[data-code]").forEach((button) => {
      button.addEventListener("click", async () => {
        document.getElementById("tx-code").value = button.dataset.code;
        await runCode();
      });
    });

    document.getElementById("run-code").addEventListener("click", runCode);

    document.getElementById("customer-context").addEventListener("dblclick", async () => {
      const reason = value("lookup-reason");
      const result = await api("/api/staff/pii/unmask", {
        method: "POST",
        body: JSON.stringify({
          customerId: value("staff-customer-id"),
          requestedBy: "manager01",
          actorRole: "BRANCH_MANAGER",
          reason,
          screenId: "CST-002"
        })
      });
      document.getElementById("customer-context").innerHTML = `
        <div class="kv"><span>Customer</span><strong>${result.item.name}</strong></div>
        <div class="kv"><span>Phone</span><span>${result.item.phone}</span></div>
        <div class="kv"><span>TTL</span><span>${result.expiresInSeconds}s</span></div>
      `;
      await refreshSidePanels();
    });

    document.getElementById("run-code").addEventListener("keydown", async (event) => {
      if (event.key === "Enter") {
        await runCode();
      }
    });

    await refreshSidePanels();
    await runCode();
  }

  async function renderCustomerWeb() {
    shell(() => `
      <section class="workspace portal">
        ${nav(["Accounts", "Transfers", "Limits", "Complaints", "Security"], "Accounts")}
        <section class="panel">
          <div class="panel-header">
            <h1>Customer Banking Workspace</h1>
            <span class="badge ok" id="customer-session">Not signed in</span>
          </div>
          <div class="panel-body business-screen">
            <div class="form-grid">
              <label>Mock customer
                <select id="customer-user">
                  <option value="customer01">customer01</option>
                </select>
              </label>
              <button id="customer-login">Sign in</button>
              <button class="secondary" id="complaint-entry">Complaint entry</button>
            </div>
            <table class="data-table">
              <thead><tr><th>Account</th><th>Status</th><th>Ledger</th><th>Available</th></tr></thead>
              <tbody id="account-list"><tr><td colspan="4">Loading accounts...</td></tr></tbody>
            </table>
            <div class="mini-card">
              <h3>Account Detail</h3>
              <div class="stack" id="account-detail">No account selected</div>
            </div>
            <table class="data-table">
              <thead><tr><th>Transaction</th><th>Type</th><th>Status</th><th>Posting</th></tr></thead>
              <tbody id="customer-transactions"><tr><td colspan="4">No transaction history</td></tr></tbody>
            </table>
            <div class="mini-card">
              <h3>Transfer Simulation</h3>
              <div class="form-grid">
                <label>From account
                  <input id="transfer-from" value="ACC-SYN-001-001">
                </label>
                <label>To account
                  <input id="transfer-to" value="ACC-SYN-002-001">
                </label>
                <label>Amount
                  <input id="transfer-amount" value="10000">
                </label>
                <label>Idempotency key
                  <input id="transfer-key" value="WEB-DEMO-001">
                </label>
                <button id="transfer-submit">Submit</button>
              </div>
              <div class="stack" id="transfer-result" style="margin-top:10px">No transfer result</div>
            </div>
            <table class="data-table">
              <thead><tr><th>Result</th><th>Status</th><th>Reference</th><th>Message</th></tr></thead>
              <tbody id="transfer-results"><tr><td colspan="4">No transfer results</td></tr></tbody>
            </table>
          </div>
        </section>
      </section>
    `);

    const customerId = "SYN-CUS-001";

    async function loadAccounts() {
      const accounts = await api(`/api/customer/accounts?customerId=${customerId}`);
      document.getElementById("account-list").innerHTML = accounts.items.map((account) => `
        <tr>
          <td>${account.maskedAccountNo}</td>
          <td><span class="badge ok">${account.status}</span></td>
          <td>${money(account.ledgerBalanceMinor)}</td>
          <td>${money(account.availableBalanceMinor)}</td>
        </tr>
      `).join("");
      if (accounts.items[0]) {
        document.getElementById("transfer-from").value = accounts.items[0].accountId;
        await loadAccountDetail(accounts.items[0].accountId);
        await loadTransactions(accounts.items[0].accountId);
      }
    }

    async function loadAccountDetail(accountId) {
      const detail = await api(`/api/customer/accounts/${accountId}/detail?customerId=${customerId}`);
      document.getElementById("account-detail").innerHTML = `
        <div class="kv"><span>Account</span><strong>${detail.item.maskedAccountNo}</strong></div>
        <div class="kv"><span>Ledger</span><span>${money(detail.item.ledgerBalanceMinor)}</span></div>
        <div class="kv"><span>Available</span><span>${money(detail.item.availableBalanceMinor)}</span></div>
        <div class="kv"><span>Audit</span><span>${detail.auditEventId}</span></div>
      `;
    }

    async function loadTransactions(accountId) {
      const history = await api(`/api/customer/transactions?customerId=${customerId}&accountId=${accountId}`);
      document.getElementById("customer-transactions").innerHTML = history.items.map((transaction) => {
        const posting = transaction.postings[0] || {};
        return `
          <tr>
            <td>${transaction.transactionId}</td>
            <td>${transaction.transactionType}</td>
            <td><span class="badge ok">${transaction.status}</span></td>
            <td>${posting.direction || ""} ${money(posting.amountMinor || 0)}</td>
          </tr>
        `;
      }).join("") || `<tr><td colspan="4">No transaction history</td></tr>`;
    }

    async function loadTransferResults() {
      const results = await api(`/api/customer/transfers?customerId=${customerId}`);
      document.getElementById("transfer-results").innerHTML = results.items.slice(-8).reverse().map((item) => `
        <tr>
          <td>${item.resultId}</td>
          <td><span class="badge ${item.status === "POSTED" ? "ok" : "warn"}">${item.status}</span></td>
          <td>${item.transactionId || item.caseId || item.failureCode || ""}</td>
          <td>${item.message}</td>
        </tr>
      `).join("") || `<tr><td colspan="4">No transfer results</td></tr>`;
    }

    document.getElementById("customer-login").addEventListener("click", async () => {
      const result = await api("/api/customer/login", {
        method: "POST",
        body: JSON.stringify({ userId: document.getElementById("customer-user").value })
      });
      document.getElementById("customer-session").textContent = result.session.userId;
    });

    document.getElementById("complaint-entry").addEventListener("click", () => {
      window.location.href = "/complaint-portal";
    });

    document.getElementById("transfer-submit").addEventListener("click", async () => {
      const amountMinor = Number(document.getElementById("transfer-amount").value);
      const idempotencyKey = document.getElementById("transfer-key").value;
      const result = await api("/api/customer/transfers", {
        method: "POST",
        body: JSON.stringify({
          fromAccountId: document.getElementById("transfer-from").value,
          toAccountId: document.getElementById("transfer-to").value,
          amountMinor,
          idempotencyKey,
          requestedBy: customerId
        })
      });
      document.getElementById("transfer-result").innerHTML = `
        <div class="kv"><span>Status</span><strong>${result.item.status}</strong></div>
        <div class="kv"><span>Reference</span><span>${result.item.transactionId || result.item.caseId || result.item.failureCode || ""}</span></div>
        <div class="kv"><span>Message</span><span>${result.item.message}</span></div>
      `;
      await loadAccounts();
      await loadTransferResults();
    });

    await api("/api/customer/login", {
      method: "POST",
      body: JSON.stringify({ userId: "customer01" })
    }).then((result) => {
      document.getElementById("customer-session").textContent = result.session.userId;
    });
    await loadAccounts();
    await loadTransferResults();
  }

  async function renderComplaintPortal() {
    shell(() => `
      <section class="workspace portal">
        ${nav(["Intake", "Status", "Attachments", "Answer"], "Intake")}
        <section class="panel">
          <div class="panel-header">
            <h1>Complaint Intake</h1>
            <span class="badge warn">Workflow controlled</span>
          </div>
          <div class="panel-body business-screen">
            <div class="form-grid">
              <label>Customer ID
                <input id="complaint-customer" value="SYN-CUS-001">
              </label>
              <label>Category
                <select id="complaint-category">
                  <option>TRANSFER_DISPUTE</option>
                  <option>ACCOUNT_ACCESS</option>
                  <option>FEE_INQUIRY</option>
                </select>
              </label>
            </div>
            <label>Complaint text
              <textarea id="complaint-text" rows="5">Synthetic complaint for workflow verification.</textarea>
            </label>
            <button id="complaint-submit">Submit complaint</button>
            <button class="secondary" id="complaint-confirm">Confirm answered case</button>
            <table class="data-table">
              <thead><tr><th>Case ID</th><th>Category</th><th>Status</th><th>Owner</th><th>SLA</th></tr></thead>
              <tbody id="complaint-list"><tr><td colspan="5">Loading complaints...</td></tr></tbody>
            </table>
            <div class="mini-card">
              <h3>Case Timeline</h3>
              <div class="stack" id="complaint-timeline">No selected case</div>
            </div>
            <div class="mini-card">
              <h3>Customer Answer</h3>
              <div class="stack" id="complaint-answer">No answer yet</div>
            </div>
          </div>
        </section>
      </section>
    `);

    function renderComplaintDetail(item) {
      document.getElementById("complaint-timeline").innerHTML = (item.timeline || []).slice(-8).map((entry) => `
        <div class="kv"><span>${entry.to || entry.type}</span><span>${entry.actorId || ""} ${entry.at || ""}</span></div>
      `).join("") || "No timeline";
      document.getElementById("complaint-answer").innerHTML = item.answer
        ? `<div class="kv"><span>Answered</span><span>${item.answer.body}</span></div>`
        : `<div class="kv"><span>Status</span><span>${item.status}</span></div>`;
    }

    async function loadComplaints() {
      const complaints = await api("/api/complaints?customerId=SYN-CUS-001");
      document.getElementById("complaint-list").innerHTML = complaints.items.map((item) => `
        <tr>
          <td>${item.caseId}</td>
          <td>${item.category}</td>
          <td><span class="badge warn">${item.status}</span></td>
          <td>${item.owner || ""}</td>
          <td>${item.slaDueAt || `${item.slaHours}h`}</td>
        </tr>
      `).join("");
      if (complaints.items[0]) {
        renderComplaintDetail(complaints.items[0]);
      }
    }

    document.getElementById("complaint-submit").addEventListener("click", async () => {
      await api("/api/complaints", {
        method: "POST",
        body: JSON.stringify({
          customerId: document.getElementById("complaint-customer").value,
          category: document.getElementById("complaint-category").value,
          description: document.getElementById("complaint-text").value
        })
      });
      await loadComplaints();
    });

    document.getElementById("complaint-confirm").addEventListener("click", async () => {
      const complaints = await api("/api/complaints?customerId=SYN-CUS-001");
      const answered = complaints.items.find((item) => item.status === "ANSWERED");
      if (answered) {
        const result = await api(`/api/customer/complaints/${answered.caseId}/confirm`, {
          method: "POST",
          body: JSON.stringify({
            customerId: "SYN-CUS-001",
            note: "Customer confirmed answer"
          })
        });
        renderComplaintDetail(result.item);
      }
      await loadComplaints();
    });

    await loadComplaints();
  }

  async function renderConsole() {
    const screens = await api(`/api/screens?app=${appName}`);
    shell(() => `
      <section class="workspace portal">
        ${nav(["Dashboard", "Cases", "Reports", "Parameters"], "Dashboard")}
        <section class="panel">
          <div class="panel-header">
            <h1>${appTitles[appName]}</h1>
            <span class="badge audit">Manifest shell</span>
          </div>
          <div class="panel-body">
            ${screens.items.length
              ? screens.items.map((screen) => `<div class="mini-card"><h3>${screen.screenId} ${screen.title}</h3><p>${screen.type} / ${screen.domain}</p></div>`).join("")
              : `<div class="empty-state">No manifests registered yet.</div>`}
          </div>
        </section>
      </section>
    `);
  }

  const renderers = {
    "customer-web": renderCustomerWeb,
    "staff-terminal": renderStaffTerminal,
    "complaint-portal": renderComplaintPortal,
    "ops-console": renderConsole,
    "audit-console": renderConsole,
    "fds-aml-console": renderConsole
  };

  renderers[appName]().catch((error) => {
    root.innerHTML = `<main class="app-shell"><section class="panel"><div class="panel-body">${error.message}</div></section></main>`;
  });
})();
