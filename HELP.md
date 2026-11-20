# Pennywise Expense Tracker

A personal INR expense tracker built with Java 17-compatible code, Spring Boot, and a responsive browser UI. A JDK 17 or newer is required to build and run it.

## Run locally

From the `demo` directory:

```powershell
.\gradlew.bat bootRun
```

Open [http://localhost:8080](http://localhost:8080). The app automatically creates a private profile for that browser; no login is required. On an existing installation with one prior login, its bank accounts, expenses, and custom categories are retained in the first browser profile. The embedded H2 database is stored under `demo/data/` and keeps data between restarts.

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
- Each browser gets its own private profile automatically. Use a short-lived one-time code to link your own devices to the same profile.
- Install the responsive app on a phone as a PWA. Its offline shell does not cache private API data.

## Phone installation and remote testing

The local address is only reachable on the computer running the app. For a temporary cross-network demo, expose it through an HTTPS tunnel. Each person who opens the URL in their own browser gets a separate profile automatically. Anyone using the same browser profile can access its data. To use your own profile on a phone, generate a one-time code from the device that has your data and enter it on the phone within 10 minutes. Linking does not merge existing data, and a browser with existing accounts cannot be linked. If all linked browser cookies are lost, the profile cannot be recovered. Keep the computer, app, and tunnel running while testing.

- Android: open the HTTPS URL in Chrome and choose **Install app** or **Add to Home screen**.
- iPhone: open the HTTPS URL in Safari, tap **Share**, then **Add to Home Screen**.

The H2 database remains on the host computer, so this temporary setup is for testing rather than permanent hosting.

## API

- `GET /api/dashboard?period=month&date=YYYY-MM-DD`
- `GET /api/auth/csrf`, `GET /api/auth/me` (creates or restores the browser profile)
- `POST /api/auth/device-link-codes`, `POST /api/auth/device-links`
- `GET` / `POST /api/accounts`
- `DELETE /api/accounts/{accountId}` (also deletes that account's expenses)
- `GET` / `POST /api/categories`
- `GET` / `POST /api/expenses`
- `DELETE /api/expenses/{expenseId}`
- `GET /api/accounts/{accountId}/export?period=month&date=YYYY-MM-DD`

Valid periods are `day`, `week`, `month`, and `year`. The date selects the day or the containing week, month, or year.
