# Pennywise Expense Tracker

A personal INR expense tracker built with Java 17-compatible code, Spring Boot, and a responsive browser UI. A JDK 17 or newer is required to build and run it.

## Run locally

From the `demo` directory:

```powershell
.\gradlew.bat bootRun
```

Open [http://localhost:8080](http://localhost:8080). The embedded H2 database is stored under `demo/data/` and keeps accounts, categories, and expenses between restarts.

Run the test suite with:

```powershell
.\gradlew.bat test
```

## Features

- Add multiple bank accounts with their current INR balances.
- Log and delete dated expenses; adding an expense reduces that account's balance and deleting it restores the amount.
- Remove a bank account; after confirmation, its linked expense history is permanently deleted too.
- Browse category spending with daily, weekly, monthly, or yearly date filters.
- Use the built-in expense categories or create custom categories.
- View a separate spending pie chart and download an `.xlsx` report for each bank account.

## API

- `GET /api/dashboard?period=month&date=YYYY-MM-DD`
- `GET` / `POST /api/accounts`
- `DELETE /api/accounts/{accountId}` (also deletes that account's expenses)
- `GET` / `POST /api/categories`
- `GET` / `POST /api/expenses`
- `DELETE /api/expenses/{expenseId}`
- `GET /api/accounts/{accountId}/export?period=month&date=YYYY-MM-DD`

Valid periods are `day`, `week`, `month`, and `year`. The date selects the day or the containing week, month, or year.
