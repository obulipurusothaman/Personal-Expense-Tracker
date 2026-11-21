const palette = ["#7568e8", "#4cba91", "#f0a45f", "#6395df", "#e36f8c", "#42a9b5", "#9d71d1", "#d0b54e"];
const periodNames = { day: "today", week: "this week", month: "this month", year: "this year" };
const localToday = new Date();
const state = {
  period: "month",
  date: `${localToday.getFullYear()}-${String(localToday.getMonth() + 1).padStart(2, "0")}-${String(localToday.getDate()).padStart(2, "0")}`,
  categoryId: "",
  accountId: ""
};
const $ = (selector) => document.querySelector(selector);
const OFFLINE_DB_NAME = "pennywise-offline";
let profileId = null;
let offlineDbPromise;
let syncRunning = false;
let toastTimer;
let installPrompt = null;
let availableAccounts = [];
let availableCategories = [];
let importRows = [];

function offlineDb() {
  if (!offlineDbPromise) {
    offlineDbPromise = new Promise((resolve, reject) => {
      const request = indexedDB.open(OFFLINE_DB_NAME, 1);
      request.onupgradeneeded = () => {
        const database = request.result;
        database.createObjectStore("settings", { keyPath: "key" });
        database.createObjectStore("snapshots", { keyPath: "profileId" });
        database.createObjectStore("mutations", { keyPath: "id" });
      };
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error || new Error("Offline storage could not be opened."));
    });

    function openImportDialog() {
      $("#import-form").reset();
      $("#import-review").hidden = true;
      $("#import-rows").replaceChildren();
      $("#import-message").textContent = "";
      $("#commit-import").hidden = true;
      $("#review-import").textContent = "Review file";
      importRows = [];
      populateImportAccounts();
      $("#import-dialog").showModal();
    }

    function populateImportAccounts(selectedId = "") {
      const accountSelect = $("#import-account");
      accountSelect.innerHTML = availableAccounts.length
        ? `<option value="">Choose an account</option>${availableAccounts.map((account) =>
          `<option value="${account.id}">${escapeHtml(account.bankName)}</option>`).join("")}`
        : `<option value="">Add a bank account first</option>`;
      accountSelect.value = selectedId ? String(selectedId) : "";
      accountSelect.disabled = availableAccounts.length === 0;
    }

    function renderImportPreview(preview) {
      populateImportAccounts(preview.suggestedAccountId || "");
      $("#import-detection").textContent = `${preview.detectedBankName || "Bank not detected"} · ${preview.accountMatchMessage}`;
      importRows = preview.rows.map((row) => ({
        ...row,
        clientRequestId: crypto.randomUUID(),
        include: row.include
      }));
      const categoryOptions = availableCategories.map((category) =>
        `<option value="${category.id}">${escapeHtml(category.name)}</option>`).join("");
      $("#import-rows").innerHTML = importRows.map((row, index) => `
        <tr data-row-index="${index}" class="${row.duplicate ? "import-duplicate" : ""}">
          <td><input class="import-include" type="checkbox" aria-label="Include ${escapeHtml(row.description)}" ${row.include ? "checked" : ""}></td>
          <td><span class="import-description">${escapeHtml(row.description)}</span>
            ${row.duplicate ? `<span class="import-warning">Possible duplicate</span>` : ""}
            ${row.assumedDebit ? `<span class="import-warning">Check debit/credit</span>` : ""}
          </td>
          <td>${escapeHtml(row.expenseDate)}</td>
          <td><select class="import-category" aria-label="Category for ${escapeHtml(row.description)}" data-category-review="${row.needsReview}">
            ${categoryOptions}
          </select></td>
          <td class="amount-cell">${money(row.amount)}</td>
        </tr>`).join("");
      importRows.forEach((row, index) => {
        const select = $(`#import-rows tr[data-row-index="${index}"] .import-category`);
        select.value = String(row.suggestedCategoryId);
      });
      $("#import-review").hidden = false;
      $("#commit-import").hidden = false;
      $("#review-import").textContent = "Review again";
      $("#import-message").textContent = "";
      const duplicates = importRows.filter((row) => row.duplicate).length;
      const assumedDebits = importRows.filter((row) => row.assumedDebit).length;
      const skipped = [
        preview.skippedCredits ? `${preview.skippedCredits} credit${preview.skippedCredits === 1 ? "" : "s"} skipped` : "",
        preview.skippedRows ? `${preview.skippedRows} unreadable row${preview.skippedRows === 1 ? "" : "s"}` : "",
        duplicates ? `${duplicates} possible duplicate${duplicates === 1 ? "" : "s"} unchecked` : "",
        assumedDebits ? `${assumedDebits} row${assumedDebits === 1 ? "" : "s"} need debit/credit review` : ""
      ].filter(Boolean);
      $("#import-summary").textContent = `${importRows.length} expense row${importRows.length === 1 ? "" : "s"} found${skipped.length ? ` · ${skipped.join(" · ")}` : ""}.`;
      updateImportButton();
    }

    function updateImportButton() {
      const accountId = $("#import-account").value;
      const selectedCount = importRows.filter((row) => row.include).length;
      $("#commit-import").disabled = !accountId || selectedCount === 0 || !navigator.onLine;
      $("#commit-import").textContent = `Import ${selectedCount} selected`;
    }

    $("#import-file").addEventListener("change", () => {
      importRows = [];
      $("#import-review").hidden = true;
      $("#commit-import").hidden = true;
      $("#review-import").textContent = "Review file";
      $("#import-message").textContent = "";
    });

    $("#import-form").addEventListener("submit", async (event) => {
      event.preventDefault();
      const file = $("#import-file").files[0];
      if (!file) return;
      if (!navigator.onLine) {
        $("#import-message").textContent = "Connect to the internet to review and import a statement.";
        return;
      }
      const formData = new FormData();
      formData.set("file", file);
      const reviewButton = $("#review-import");
      reviewButton.disabled = true;
      $("#import-message").textContent = "";
      try {
        const preview = await api("/api/imports/preview", { method: "POST", body: formData });
        renderImportPreview(preview);
      } catch (error) {
        $("#import-message").textContent = error.message;
      } finally {
        reviewButton.disabled = false;
      }
    });

    $("#import-rows").addEventListener("change", (event) => {
      const row = event.target.closest("tr[data-row-index]");
      if (!row) return;
      const index = Number(row.dataset.rowIndex);
      if (event.target.matches(".import-include")) {
        importRows[index].include = event.target.checked;
      } else if (event.target.matches(".import-category")) {
        importRows[index].suggestedCategoryId = Number(event.target.value);
      }
      updateImportButton();
    });

    $("#import-account").addEventListener("change", updateImportButton);
    $("#commit-import").addEventListener("click", async () => {
      const accountId = Number($("#import-account").value);
      const rows = importRows.filter((row) => row.include).map((row) => ({
        categoryId: Number(row.suggestedCategoryId),
        description: row.description,
        amount: Number(row.amount),
        expenseDate: row.expenseDate,
        clientRequestId: row.clientRequestId
      }));
      if (!accountId || !rows.length || !navigator.onLine) {
        updateImportButton();
        return;
      }
      const button = $("#commit-import");
      button.disabled = true;
      try {
        const result = await api("/api/imports", {
          method: "POST",
          body: JSON.stringify({ accountId, rows })
        });
        $("#import-dialog").close();
        await refreshDashboard();
        showToast(`Imported ${result.imported} expense${result.imported === 1 ? "" : "s"}; account balance unchanged.`);
      } catch (error) {
        $("#import-message").textContent = error.message;
      } finally {
        updateImportButton();
      }
    });
  }
  return offlineDbPromise;
}

function storeRequest(storeName, mode, action) {
  return offlineDb().then((database) => new Promise((resolve, reject) => {
    const transaction = database.transaction(storeName, mode);
    const request = action(transaction.objectStore(storeName));
    let result;
    request.onsuccess = () => { result = request.result; };
    request.onerror = () => reject(request.error || new Error("Offline storage request failed."));
    transaction.oncomplete = () => resolve(result);
    transaction.onerror = () => reject(transaction.error || new Error("Offline storage transaction failed."));
    transaction.onabort = () => reject(transaction.error || new Error("Offline storage transaction was aborted."));
  }));
}

function getSetting(key) {
  return storeRequest("settings", "readonly", (store) => store.get(key))
    .then((record) => record?.value ?? null);
}

function saveSetting(key, value) {
  return storeRequest("settings", "readwrite", (store) => store.put({ key, value }));
}

function loadOfflineSnapshot() {
  return profileId
    ? storeRequest("snapshots", "readonly", (store) => store.get(profileId)).then((record) => record?.data ?? null)
    : Promise.resolve(null);
}

function saveOfflineSnapshot(snapshot) {
  if (!profileId) return Promise.resolve();
  return storeRequest("snapshots", "readwrite", (store) => store.put({
    profileId,
    savedAt: new Date().toISOString(),
    data: snapshot
  }));
}

async function getPendingMutations() {
  if (!profileId) return [];
  const records = await storeRequest("mutations", "readonly", (store) => store.getAll());
  return records.filter((record) => record.profileId === profileId)
    .sort((first, second) => first.createdAt.localeCompare(second.createdAt));
}

function savePendingMutation(mutation) {
  return storeRequest("mutations", "readwrite", (store) => store.put(mutation));
}

function removePendingMutation(id) {
  return storeRequest("mutations", "readwrite", (store) => store.delete(id));
}

function isNetworkError(error) {
  return !navigator.onLine || error instanceof TypeError;
}

function updateConnectionStatus(pendingCount) {
  const status = $("#connection-status");
  const syncButton = $("#sync-now");
  const message = $("#connection-message");
  status.hidden = false;
  syncButton.disabled = !navigator.onLine || pendingCount === 0;
  if (!navigator.onLine) {
    message.textContent = pendingCount
      ? `Offline · ${pendingCount} change${pendingCount === 1 ? "" : "s"} waiting to sync.`
      : "Offline · changes stay on this device until you reconnect.";
    return;
  }
  if (pendingCount) {
    message.textContent = `${pendingCount} change${pendingCount === 1 ? "" : "s"} waiting to sync.`;
    return;
  }
  message.textContent = "Online · all changes synced.";
}

function escapeHtml(value) {
  return String(value ?? "").replace(/[&<>"']/g, (character) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
  })[character]);
}

function money(value) {
  return new Intl.NumberFormat("en-IN", {
    style: "currency", currency: "INR", maximumFractionDigits: 2
  }).format(Number(value || 0));
}

function shortDate(value) {
  return new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short", year: "numeric" })
    .format(new Date(`${value}T00:00:00`));
}

function readCookie(name) {
  const prefix = `${name}=`;
  const cookie = document.cookie.split("; ").find((value) => value.startsWith(prefix));
  return cookie ? decodeURIComponent(cookie.slice(prefix.length)) : "";
}

async function csrfToken() {
  const existing = readCookie("XSRF-TOKEN");
  if (existing) return existing;
  const response = await fetch("/api/auth/csrf", { credentials: "same-origin" });
  if (!response.ok) throw new Error("Could not initialize a secure session.");
  const body = await response.json();
  return readCookie("XSRF-TOKEN") || body.token;
}

async function api(url, options = {}) {
  const method = (options.method || "GET").toUpperCase();
  const headers = new Headers(options.headers || {});
  if (options.body && !(options.body instanceof FormData) && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  if (!["GET", "HEAD", "OPTIONS"].includes(method)) {
    headers.set("X-XSRF-TOKEN", await csrfToken());
  }
  const response = await fetch(url, {
    ...options,
    method,
    credentials: "same-origin",
    headers
  });
  if (!response.ok) {
    let message = "Something went wrong. Please try again.";
    try {
      const body = await response.json();
      message = body.detail || body.message || message;
    } catch { /* The response may not contain JSON. */ }
    const error = new Error(message);
    error.status = response.status;
    throw error;
  }
  if (response.status === 204) return null;
  return response.json();
}

function showApp() {
  $("#startup-screen").hidden = true;
  $("#app-shell").hidden = false;
}

function showStartupError(message) {
  $("#app-shell").hidden = true;
  $("#startup-screen").hidden = false;
  $("#startup-title").textContent = "Could not open your wallet";
  $("#startup-message").textContent = message;
  $("#retry-app").hidden = false;
}

function showToast(message, isError = false) {
  const toast = $("#toast");
  toast.textContent = message;
  toast.classList.toggle("error", isError);
  toast.classList.add("visible");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toast.classList.remove("visible"), 3000);
}

function queryString() {
  return new URLSearchParams({ period: state.period, date: state.date }).toString();
}

async function refreshDashboard() {
  let snapshot;
  if (navigator.onLine) {
    try {
      snapshot = await api("/api/offline/snapshot");
      await saveOfflineSnapshot(snapshot);
    } catch (error) {
      if (!isNetworkError(error)) throw error;
      snapshot = await loadOfflineSnapshot();
      if (!snapshot) throw new Error("Connect to the internet once to save this wallet for offline use.");
    }
  } else {
    snapshot = await loadOfflineSnapshot();
    if (!snapshot) throw new Error("Connect to the internet once to save this wallet for offline use.");
  }
  const mutations = await getPendingMutations();
  const { dashboard, expenses } = buildOfflineView(snapshot, mutations);
  renderDashboard(dashboard);
  renderExpenses(expenses);
  updateConnectionStatus(mutations.length);
}

function toLocalDateString(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

function selectedDateRange() {
  const selected = new Date(`${state.date}T00:00:00`);
  let start = new Date(selected);
  let end = new Date(selected);
  if (state.period === "week") {
    const daysSinceMonday = (selected.getDay() + 6) % 7;
    start.setDate(selected.getDate() - daysSinceMonday);
    end = new Date(start);
    end.setDate(start.getDate() + 6);
  } else if (state.period === "month") {
    start = new Date(selected.getFullYear(), selected.getMonth(), 1);
    end = new Date(selected.getFullYear(), selected.getMonth() + 1, 0);
  } else if (state.period === "year") {
    start = new Date(selected.getFullYear(), 0, 1);
    end = new Date(selected.getFullYear(), 11, 31);
  }
  return [toLocalDateString(start), toLocalDateString(end)];
}

function buildOfflineView(snapshot, mutations) {
  const accountsById = new Map(snapshot.accounts.map((account) => [String(account.id), account]));
  const categoriesById = new Map(snapshot.categories.map((category) => [String(category.id), category]));
  const deletedIds = new Set(mutations
    .filter((mutation) => mutation.type === "delete-expense")
    .map((mutation) => String(mutation.expenseId)));
  const pendingCreates = mutations.filter((mutation) => mutation.type === "create-expense");
  const expenses = snapshot.expenses.filter((expense) => !deletedIds.has(String(expense.id)));
  for (const mutation of pendingCreates) {
    const body = mutation.body;
    const account = accountsById.get(String(body.accountId));
    const category = categoriesById.get(String(body.categoryId));
    if (!account || !category) continue;
    expenses.push({
      id: `local:${mutation.id}`,
      accountId: account.id,
      bankName: account.bankName,
      categoryId: category.id,
      categoryName: category.name,
      description: body.description?.trim() || category.name,
      amount: body.amount,
      expenseDate: body.expenseDate,
      balanceAdjusted: true,
      pendingAt: mutation.createdAt
    });
  }

  const [startDate, endDate] = selectedDateRange();
  const inPeriod = expenses.filter((expense) => expense.expenseDate >= startDate && expense.expenseDate <= endDate);
  const totalSpent = inPeriod.reduce((total, expense) => total + Number(expense.amount), 0);
  const dashboardAccounts = snapshot.accounts.map((account) => {
    const accountExpenses = inPeriod.filter((expense) => String(expense.accountId) === String(account.id));
    const categoryTotals = new Map();
    for (const expense of accountExpenses) {
      const key = String(expense.categoryId);
      const current = categoryTotals.get(key) || { categoryId: expense.categoryId, categoryName: expense.categoryName, amount: 0 };
      current.amount += Number(expense.amount);
      categoryTotals.set(key, current);
    }
    const createdForAccount = pendingCreates
      .filter((mutation) => String(mutation.body.accountId) === String(account.id))
      .reduce((total, mutation) => total + Number(mutation.body.amount), 0);
    const deletedForAccount = snapshot.expenses
      .filter((expense) => deletedIds.has(String(expense.id))
        && String(expense.accountId) === String(account.id)
        && expense.balanceAdjusted !== false)
      .reduce((total, expense) => total + Number(expense.amount), 0);
    return {
      id: account.id,
      bankName: account.bankName,
      balance: Number(account.balance) - createdForAccount + deletedForAccount,
      spent: accountExpenses.reduce((total, expense) => total + Number(expense.amount), 0),
      categoryBreakdown: [...categoryTotals.values()].sort((first, second) => second.amount - first.amount),
      recentExpenses: accountExpenses.slice().sort(compareExpenses).slice(0, 5)
    };
  });
  const filteredExpenses = inPeriod
    .filter((expense) => !state.accountId || String(expense.accountId) === state.accountId)
    .filter((expense) => !state.categoryId || String(expense.categoryId) === state.categoryId)
    .sort(compareExpenses);
  return {
    dashboard: { totalSpent, accounts: dashboardAccounts, categories: snapshot.categories },
    expenses: filteredExpenses
  };
}

function compareExpenses(first, second) {
  if (first.expenseDate !== second.expenseDate) return second.expenseDate.localeCompare(first.expenseDate);
  if (first.pendingAt || second.pendingAt) {
    return (second.pendingAt || "").localeCompare(first.pendingAt || "");
  }
  return Number(second.id) - Number(first.id);
}

function renderDashboard(dashboard) {
  availableAccounts = dashboard.accounts;
  availableCategories = dashboard.categories;
  $("#total-spent").textContent = money(dashboard.totalSpent);
  $("#account-count").textContent = dashboard.accounts.length;
  $("#account-caption").textContent = dashboard.accounts.length
    ? `${dashboard.accounts.length === 1 ? "One account" : `${dashboard.accounts.length} accounts`} connected`
    : "Add your first account";
  $("#spent-period-label").textContent = `Spent ${periodNames[state.period]}`;
  $("#bank-balance-cards").innerHTML = dashboard.accounts.length
    ? dashboard.accounts.map((account, index) => `
      <article class="summary-card balance-card">
        <div class="summary-heading">
          <span>${escapeHtml(account.bankName)}</span>
          <span class="summary-icon mint">#${index + 1}</span>
        </div>
        <div class="summary-value">${money(account.balance)}</div>
        <div class="summary-foot"><span class="status-dot"></span>Available in this account</div>
        <div class="card-decoration balance-decoration"></div>
      </article>`).join("")
    : `<article class="summary-card balance-card">
        <div class="summary-heading"><span>Balances by bank</span><span class="summary-icon mint">₹</span></div>
        <div class="summary-value">No accounts yet</div>
        <div class="summary-foot">Add a bank account to see its balance here.</div>
        <div class="card-decoration balance-decoration"></div>
      </article>`;

  const categoryFilter = $("#category-filter");
  const selectedCategory = state.categoryId;
  categoryFilter.innerHTML = `<option value="">All categories</option>${dashboard.categories.map((category) =>
    `<option value="${category.id}">${escapeHtml(category.name)}</option>`).join("")}`;
  categoryFilter.value = selectedCategory;

  const accountFilter = $("#account-filter");
  const selectedAccount = state.accountId;
  accountFilter.innerHTML = `<option value="">All accounts</option>${dashboard.accounts.map((account) =>
    `<option value="${account.id}">${escapeHtml(account.bankName)}</option>`).join("")}`;
  accountFilter.value = selectedAccount;

  const accountSelect = $("#expense-form [name=accountId]");
  accountSelect.innerHTML = dashboard.accounts.map((account) =>
    `<option value="${account.id}">${escapeHtml(account.bankName)} · ${money(account.balance)}</option>`).join("");
  const categorySelect = $("#expense-form [name=categoryId]");
  categorySelect.innerHTML = dashboard.categories.map((category) =>
    `<option value="${category.id}">${escapeHtml(category.name)}</option>`).join("");

  const grid = $("#accounts-grid");
  if (!dashboard.accounts.length) {
    grid.innerHTML = `<div class="empty-state account-empty"><span class="empty-icon">₹</span><strong>Your accounts start here</strong><span>Add a bank account to see its balance and spending breakdown.</span><button class="button button-quiet" id="empty-add-account">+ Add a bank account</button></div>`;
  } else {
    grid.innerHTML = dashboard.accounts.map((account, index) => {
      const shortName = account.bankName.split(/\s+/).slice(0, 2).map((part) => part[0]).join("").toUpperCase();
      const legend = account.categoryBreakdown.slice(0, 5).map((category, itemIndex) =>
        `<div class="legend-item"><span class="legend-swatch" style="background:${palette[itemIndex % palette.length]}"></span><span class="legend-name">${escapeHtml(category.categoryName)}</span><span class="legend-amount">${money(category.amount)}</span></div>`).join("");
      return `<article class="account-card">
        <div class="account-card-head">
          <div class="bank-mark color-${index % 4}">${escapeHtml(shortName)}</div>
          <div class="bank-info"><strong>${escapeHtml(account.bankName)}</strong><span>${account.recentExpenses.length ? `${account.recentExpenses.length} recent expense${account.recentExpenses.length === 1 ? "" : "s"}` : "Ready to track"}</span></div>
          <div class="bank-balance"><strong>${money(account.balance)}</strong><span>Available balance</span></div>
        </div>
        <div class="account-divider"></div>
        <div class="chart-area">
          ${account.categoryBreakdown.length ? `<canvas class="chart-canvas" id="chart-${account.id}" aria-label="Spending by category for ${escapeHtml(account.bankName)}"></canvas><div class="chart-legend">${legend}</div>` : `<div class="chart-empty">No spend</div><div class="chart-empty-copy">Your category breakdown<br>will appear here.</div>`}
        </div>
        <div class="account-card-foot">
          <span>${money(account.spent)} spent ${periodNames[state.period]}</span>
          <div class="account-actions">
            <a class="export-link" href="/api/accounts/${account.id}/export?${queryString()}">↓ Export Excel</a>
            <button class="remove-account" data-account-id="${account.id}" data-bank-name="${escapeHtml(account.bankName)}" type="button">Remove</button>
          </div>
        </div>
      </article>`;
    }).join("");
    dashboard.accounts.forEach(drawAccountChart);
  }
  $("#empty-add-account")?.addEventListener("click", () => $("#account-dialog").showModal());
}

function drawAccountChart(account) {
  const canvas = $(`#chart-${account.id}`);
  if (!canvas) return;
  const context = canvas.getContext("2d");
  const size = 112;
  const scale = window.devicePixelRatio || 1;
  canvas.width = size * scale;
  canvas.height = size * scale;
  context.scale(scale, scale);
  const values = account.categoryBreakdown.slice(0, 5).map((entry) => Number(entry.amount));
  const total = values.reduce((sum, value) => sum + value, 0);
  let start = -Math.PI / 2;
  values.forEach((value, index) => {
    const end = start + (value / total) * Math.PI * 2;
    context.beginPath();
    context.moveTo(56, 56);
    context.arc(56, 56, 51, start, end);
    context.closePath();
    context.fillStyle = palette[index % palette.length];
    context.fill();
    start = end;
  });
  context.beginPath();
  context.arc(56, 56, 31, 0, Math.PI * 2);
  context.fillStyle = "#fff";
  context.fill();
  context.textAlign = "center";
  context.fillStyle = "#85899a";
  context.font = "600 8px 'DM Sans', sans-serif";
  context.fillText("SPENT", 56, 53);
  context.fillStyle = "#2f3448";
  context.font = "700 10px 'DM Sans', sans-serif";
  context.fillText(compactMoney(total), 56, 67);
}

function compactMoney(value) {
  if (value >= 100000) return `₹${(value / 100000).toFixed(1)}L`;
  if (value >= 1000) return `₹${(value / 1000).toFixed(1)}k`;
  return `₹${Math.round(value)}`;
}

function renderExpenses(expenses) {
  const rows = $("#expense-rows");
  $("#empty-transactions").classList.toggle("hidden", expenses.length > 0);
  $(".table-scroll").classList.toggle("hidden", expenses.length === 0);
  rows.innerHTML = expenses.map((expense, index) => {
    const initials = expense.categoryName.split(/\s+/).slice(0, 2).map((part) => part[0]).join("").toUpperCase();
    return `<tr>
      <td><div class="transaction-name"><span class="transaction-icon" style="background:${index % 3 === 1 ? "#eaf7f2" : index % 3 === 2 ? "#fff2e7" : "#f3f0ff"};color:${index % 3 === 1 ? "#45a181" : index % 3 === 2 ? "#d28b4d" : "#7868df"}">${escapeHtml(initials)}</span><span><span class="transaction-title">${escapeHtml(expense.categoryName)}</span><span class="transaction-description">${escapeHtml(expense.description)}</span></span></div></td>
      <td><span class="category-chip">${escapeHtml(expense.categoryName)}</span></td>
      <td>${escapeHtml(expense.bankName)}</td>
      <td>${shortDate(expense.expenseDate)}</td>
      <td class="amount-cell">${money(expense.amount)}</td>
      <td><button class="delete-expense" data-expense-id="${expense.id}" data-balance-adjusted="${expense.balanceAdjusted !== false}" title="Delete expense" aria-label="Delete ${escapeHtml(expense.description)}">×</button></td>
    </tr>`;
  }).join("");
}

function openDialog(id) {
  const dialog = $(`#${id}`);
  if (id === "expense-dialog") {
    const dateInput = $("#expense-form [name=expenseDate]");
    dateInput.value = state.date;
  }
  dialog.showModal();
}

async function submitForm(form, url, successMessage, onSuccess) {
  const formData = new FormData(form);
  const body = Object.fromEntries(formData.entries());
  if (body.amount !== undefined) body.amount = Number(body.amount);
  if (body.accountId !== undefined) body.accountId = Number(body.accountId);
  if (body.categoryId !== undefined) body.categoryId = Number(body.categoryId);
  const isExpense = url === "/api/expenses";
  if (isExpense) body.clientRequestId = crypto.randomUUID();
  try {
    await api(url, { method: "POST", body: JSON.stringify(body) });
  } catch (error) {
    if (!isNetworkError(error)) {
      showToast(error.message, true);
      return;
    }
    if (!isExpense) {
      showToast("Bank account and category changes need an internet connection.", true);
      return;
    }
    try {
      const snapshot = await loadOfflineSnapshot();
      const accountExists = snapshot?.accounts.some((account) => String(account.id) === String(body.accountId));
      const categoryExists = snapshot?.categories.some((category) => String(category.id) === String(body.categoryId));
      if (!accountExists || !categoryExists) {
        throw new Error("Connect to the internet once before adding an offline expense.");
      }
      await savePendingMutation({
        id: body.clientRequestId,
        profileId,
        type: "create-expense",
        body,
        createdAt: new Date().toISOString()
      });
      form.closest("dialog").close();
      form.reset();
      await onSuccess();
      showToast("Saved on this device. It will sync when you're back online.");
    } catch (storageError) {
      showToast(storageError.message, true);
    }
    return;
  }
  form.closest("dialog").close();
  form.reset();
  await onSuccess();
  showToast(successMessage);
}

async function syncPendingMutations() {
  if (syncRunning || !navigator.onLine || !profileId) return;
  syncRunning = true;
  try {
    const mutations = await getPendingMutations();
    for (const mutation of mutations) {
      try {
        if (mutation.type === "create-expense") {
          await api("/api/expenses", { method: "POST", body: JSON.stringify(mutation.body) });
        } else if (mutation.type === "delete-expense") {
          await api(`/api/expenses/${mutation.expenseId}`, { method: "DELETE" });
        }
      } catch (error) {
        if (!(mutation.type === "delete-expense" && error.status === 404)) throw error;
      }
      await removePendingMutation(mutation.id);
    }
    if (mutations.length) {
      const snapshot = await api("/api/offline/snapshot");
      await saveOfflineSnapshot(snapshot);
    }
    await refreshDashboard();
  } catch (error) {
    showToast(`Sync paused: ${error.message}`, true);
  } finally {
    syncRunning = false;
    updateConnectionStatus((await getPendingMutations()).length);
  }
}

$("#period-filter").addEventListener("change", (event) => {
  state.period = event.target.value;
  refreshDashboard().catch((error) => showToast(error.message, true));
});
$("#date-filter").addEventListener("change", (event) => {
  if (!event.target.value) return;
  state.date = event.target.value;
  refreshDashboard().catch((error) => showToast(error.message, true));
});
$("#category-filter").addEventListener("change", (event) => {
  state.categoryId = event.target.value;
  refreshDashboard().catch((error) => showToast(error.message, true));
});
$("#account-filter").addEventListener("change", (event) => {
  state.accountId = event.target.value;
  refreshDashboard().catch((error) => showToast(error.message, true));
});

$("#open-expense").addEventListener("click", () => openDialog("expense-dialog"));
$("#empty-add-expense").addEventListener("click", () => openDialog("expense-dialog"));
$("#open-account").addEventListener("click", () => openDialog("account-dialog"));
$("#open-category").addEventListener("click", () => openDialog("category-dialog"));
$("#open-import").addEventListener("click", () => openImportDialog());
document.querySelectorAll("[data-close]").forEach((button) => {
  button.addEventListener("click", () => $(`#${button.dataset.close}`).close());
});

$("#expense-form").addEventListener("submit", (event) => {
  event.preventDefault();
  submitForm(event.currentTarget, "/api/expenses", "Expense added and balance updated.", refreshDashboard);
});
$("#account-form").addEventListener("submit", (event) => {
  event.preventDefault();
  submitForm(event.currentTarget, "/api/accounts", "Bank account added.", refreshDashboard);
});
$("#category-form").addEventListener("submit", (event) => {
  event.preventDefault();
  submitForm(event.currentTarget, "/api/categories", "New category saved.", refreshDashboard);
});

$("#open-device-link").addEventListener("click", () => {
  $("#device-link-form").reset();
  $("#device-link-message").textContent = "";
  $("#device-link-result").hidden = true;
  openDialog("device-link-dialog");
});

$("#generate-device-link").addEventListener("click", async () => {
  $("#device-link-message").textContent = "";
  try {
    const result = await api("/api/auth/device-link-codes", { method: "POST" });
    $("#device-link-code").textContent = result.code;
    $("#device-link-result").hidden = false;
  } catch (error) {
    $("#device-link-message").textContent = error.message;
  }
});

$("#device-link-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  $("#device-link-message").textContent = "";
  const formData = new FormData(event.currentTarget);
  try {
    await api("/api/auth/device-links", {
      method: "POST",
      body: JSON.stringify({ code: String(formData.get("code")).trim() })
    });
    state.accountId = "";
    state.categoryId = "";
    $("#device-link-dialog").close();
    await refreshDashboard();
    showToast("This device now uses the linked private data.");
  } catch (error) {
    $("#device-link-message").textContent = error.message;
  }
});

$("#retry-app").addEventListener("click", () => window.location.reload());

async function installApp() {
  if (!installPrompt) {
    showToast("Use your browser menu and choose Install app or Add to Home Screen.");
    return;
  }
  await installPrompt.prompt();
  installPrompt = null;
}

$("#install-app").addEventListener("click", installApp);

window.addEventListener("beforeinstallprompt", (event) => {
  event.preventDefault();
  installPrompt = event;
});

window.addEventListener("appinstalled", () => {
  installPrompt = null;
  showToast("Pennywise was installed on this device.");
});

$("#expense-rows").addEventListener("click", async (event) => {
  const button = event.target.closest("[data-expense-id]");
  if (!button) return;
  const confirmation = button.dataset.balanceAdjusted === "false"
    ? "Delete this imported expense? The account balance will remain unchanged."
    : "Delete this expense? The account balance will be restored.";
  if (!window.confirm(confirmation)) return;
  const expenseId = button.dataset.expenseId;
  if (expenseId.startsWith("local:")) {
    await removePendingMutation(expenseId.slice("local:".length));
    await refreshDashboard();
    showToast("Offline expense removed.");
    return;
  }
  try {
    if (!navigator.onLine) throw new TypeError("Offline");
    await api(`/api/expenses/${expenseId}`, { method: "DELETE" });
  } catch (error) {
    if (!isNetworkError(error)) {
      showToast(error.message, true);
      return;
    }
    try {
      await savePendingMutation({
        id: crypto.randomUUID(),
        profileId,
        type: "delete-expense",
        expenseId,
        createdAt: new Date().toISOString()
      });
    } catch (storageError) {
      showToast(storageError.message, true);
      return;
    }
    await refreshDashboard();
    showToast("Deletion saved on this device and will sync when you're back online.");
    return;
  }
  try {
    await refreshDashboard();
    showToast("Expense deleted and balance restored.");
  } catch (error) {
    showToast(error.message, true);
  }
});

$("#accounts-grid").addEventListener("click", async (event) => {
  const button = event.target.closest("[data-account-id]");
  if (!button) return;
  const confirmed = window.confirm(
    `Remove ${button.dataset.bankName} and permanently delete all of its expenses?`
  );
  if (!confirmed) return;
  try {
    await api(`/api/accounts/${button.dataset.accountId}`, { method: "DELETE" });
    if (state.accountId === button.dataset.accountId) state.accountId = "";
    await refreshDashboard();
    showToast("Bank account and its expenses removed.");
  } catch (error) {
    showToast(error.message, true);
  }
});

async function initializeApp() {
  try {
    profileId = await getSetting("profileId");
    try {
      const currentUser = await api("/api/auth/me");
      profileId = String(currentUser.id);
      await saveSetting("profileId", profileId);
    } catch (error) {
      if (!isNetworkError(error) || !profileId || !(await loadOfflineSnapshot())) throw error;
    }
    showApp();
    await refreshDashboard();
    await syncPendingMutations();
  } catch (error) {
    showStartupError(error.message);
  }
}

$("#sync-now").addEventListener("click", syncPendingMutations);
window.addEventListener("online", syncPendingMutations);
window.addEventListener("offline", () => {
  refreshDashboard().catch((error) => showToast(error.message, true));
});

document.querySelectorAll(".nav-link").forEach((link) => {
  link.addEventListener("click", () => {
    document.querySelectorAll(".nav-link").forEach((item) => item.classList.remove("active"));
    link.classList.add("active");
  });
});

$("#date-filter").value = state.date;
initializeApp();

if ("serviceWorker" in navigator) {
  window.addEventListener("load", () => {
    navigator.serviceWorker.register("/service-worker.js")
      .catch(() => showToast("Offline app support could not be enabled.", true));
  });
}
