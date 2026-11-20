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
let toastTimer;

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

async function api(url, options = {}) {
  const response = await fetch(url, {
    ...options,
    headers: { "Content-Type": "application/json", ...(options.headers || {}) }
  });
  if (!response.ok) {
    let message = "Something went wrong. Please try again.";
    try {
      const body = await response.json();
      message = body.detail || body.message || message;
    } catch { /* The response may not contain JSON. */ }
    throw new Error(message);
  }
  if (response.status === 204) return null;
  return response.json();
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
  const [dashboard, expenses] = await Promise.all([
    api(`/api/dashboard?${queryString()}`),
    api(`/api/expenses?${queryString()}${state.accountId ? `&accountId=${state.accountId}` : ""}${state.categoryId ? `&categoryId=${state.categoryId}` : ""}`)
  ]);
  renderDashboard(dashboard);
  renderExpenses(expenses);
}

function renderDashboard(dashboard) {
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
      <td><button class="delete-expense" data-expense-id="${expense.id}" title="Delete expense" aria-label="Delete ${escapeHtml(expense.description)}">×</button></td>
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
  try {
    await api(url, { method: "POST", body: JSON.stringify(body) });
    form.closest("dialog").close();
    form.reset();
    await onSuccess();
    showToast(successMessage);
  } catch (error) {
    showToast(error.message, true);
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

$("#expense-rows").addEventListener("click", async (event) => {
  const button = event.target.closest("[data-expense-id]");
  if (!button) return;
  if (!window.confirm("Delete this expense? The account balance will be restored.")) return;
  try {
    await api(`/api/expenses/${button.dataset.expenseId}`, { method: "DELETE" });
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

document.querySelectorAll(".nav-link").forEach((link) => {
  link.addEventListener("click", () => {
    document.querySelectorAll(".nav-link").forEach((item) => item.classList.remove("active"));
    link.classList.add("active");
  });
});

$("#date-filter").value = state.date;
refreshDashboard().catch((error) => showToast(error.message, true));
