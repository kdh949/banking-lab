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
            <span class="badge audit">Reason-required lookup</span>
          </div>
          <div class="panel-body">
            <div class="terminal-grid">
              <label>Transaction code
                <input id="tx-code" value="CST-001" aria-label="Transaction code">
              </label>
              <label>Lookup reason
                <input id="lookup-reason" value="Customer requested branch support" aria-label="Lookup reason">
              </label>
              <button id="run-search">Run</button>
            </div>
          </div>
          <div class="tabs">
            ${screens.items.map((screen, index) => `<div class="tab ${index === 0 ? "active" : ""}">${screen.screenId} ${screen.title}</div>`).join("")}
          </div>
          <div class="panel-body business-screen">
            <div class="form-grid" id="screen-fields"></div>
            <table class="data-table">
              <thead><tr><th>Customer ID</th><th>Name</th><th>Phone</th><th>Risk</th><th>Audit</th></tr></thead>
              <tbody id="customer-results"><tr><td colspan="5">Run CST-001 to create a customer-search audit event.</td></tr></tbody>
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

    const fields = document.getElementById("screen-fields");
    fields.innerHTML = screens.items[0].query.fields.map((field) => `
      <label>${field.label}
        <input placeholder="${field.name}" aria-label="${field.label}">
      </label>
    `).join("");

    async function refreshSidePanels() {
      const [approvals, audits] = await Promise.all([
        api("/api/staff/approvals"),
        api("/api/staff/audit-events")
      ]);
      document.getElementById("approval-inbox").innerHTML = approvals.items.length
        ? approvals.items.map((item) => `<span class="badge warn">${item.businessType} ${item.status}</span>`).join("")
        : `<span class="badge ok">No pending approvals</span>`;
      document.getElementById("audit-log").innerHTML = audits.items.slice(-5).reverse().map((item) => `
        <div class="kv"><span>${item.eventType}</span><span>${item.reason || "system"}</span></div>
      `).join("") || `<span>No audit events yet</span>`;
    }

    document.getElementById("run-search").addEventListener("click", async () => {
      const reason = encodeURIComponent(document.getElementById("lookup-reason").value);
      const result = await api(`/api/staff/customers/search?query=synthetic&reason=${reason}`);
      document.getElementById("customer-results").innerHTML = result.items.map((customer) => `
        <tr>
          <td>${customer.customerId}</td>
          <td>${customer.maskedName}</td>
          <td>${customer.maskedPhone}</td>
          <td>${customer.riskGrade}</td>
          <td><span class="badge audit">${result.auditEventId}</span></td>
        </tr>
      `).join("");
      const selected = result.items[0];
      document.getElementById("customer-context").innerHTML = `
        <div class="kv"><span>Customer</span><strong>${selected.maskedName}</strong></div>
        <div class="kv"><span>Customer ID</span><span>${selected.customerId}</span></div>
        <div class="kv"><span>Reason</span><span>${decodeURIComponent(reason)}</span></div>
      `;
      await refreshSidePanels();
    });

    await refreshSidePanels();
  }

  async function renderCustomerWeb() {
    shell(() => `
      <section class="workspace portal">
        ${nav(["Accounts", "Transfers", "Limits", "Complaints", "Security"], "Accounts")}
        <section class="panel">
          <div class="panel-header">
            <h1>Account Overview</h1>
            <span class="badge ok">Ledger projection</span>
          </div>
          <div class="panel-body business-screen">
            <table class="data-table">
              <thead><tr><th>Account</th><th>Status</th><th>Ledger</th><th>Available</th></tr></thead>
              <tbody id="account-list"><tr><td colspan="4">Loading accounts...</td></tr></tbody>
            </table>
            <div class="mini-card">
              <h3>Transfer Simulation</h3>
              <div class="form-grid">
                <label>Amount
                  <input id="transfer-amount" value="10000">
                </label>
                <label>Idempotency key
                  <input id="transfer-key" value="WEB-DEMO-001">
                </label>
                <button id="transfer-submit">Submit</button>
              </div>
            </div>
          </div>
        </section>
      </section>
    `);

    async function loadAccounts() {
      const accounts = await api("/api/customer/accounts?customerId=SYN-CUS-001");
      document.getElementById("account-list").innerHTML = accounts.items.map((account) => `
        <tr>
          <td>${account.maskedAccountNo}</td>
          <td><span class="badge ok">${account.status}</span></td>
          <td>${money(account.ledgerBalanceMinor)}</td>
          <td>${money(account.availableBalanceMinor)}</td>
        </tr>
      `).join("");
    }

    document.getElementById("transfer-submit").addEventListener("click", async () => {
      const amountMinor = Number(document.getElementById("transfer-amount").value);
      const idempotencyKey = document.getElementById("transfer-key").value;
      await api("/api/customer/transfers", {
        method: "POST",
        body: JSON.stringify({
          fromAccountId: "ACC-SYN-001-001",
          toAccountId: "ACC-SYN-002-001",
          amountMinor,
          idempotencyKey,
          requestedBy: "SYN-CUS-001"
        })
      });
      await loadAccounts();
    });

    await loadAccounts();
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
            <table class="data-table">
              <thead><tr><th>Case ID</th><th>Category</th><th>Status</th><th>SLA</th></tr></thead>
              <tbody id="complaint-list"><tr><td colspan="4">Loading complaints...</td></tr></tbody>
            </table>
          </div>
        </section>
      </section>
    `);

    async function loadComplaints() {
      const complaints = await api("/api/complaints");
      document.getElementById("complaint-list").innerHTML = complaints.items.map((item) => `
        <tr>
          <td>${item.caseId}</td>
          <td>${item.category}</td>
          <td><span class="badge warn">${item.status}</span></td>
          <td>${item.slaHours}h</td>
        </tr>
      `).join("");
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
