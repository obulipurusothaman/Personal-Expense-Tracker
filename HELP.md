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

- Add multiple bank accounts with a starting INR balance.
- Log and delete dated expenses; manual and imported expenses reduce the remaining balance, and deleting an expense restores the amount.
- Remove a bank account; after confirmation, its linked expense history is permanently deleted too.
- Browse category spending with daily, weekly, monthly, or yearly date filters.
- Use the built-in expense categories or create custom categories.
- Import CSV, Excel (`.xlsx`), text (`.txt`), and selectable-text PDF bank statements. Scanned/image-only PDFs and password-protected PDFs are not supported. Date-grouped text ledgers are also supported when they use month/day headings and put each expense description and amount on one line; amounts joined with `+` are added together, and missing years use the current year. Set the account's starting balance to the balance before the expenses being imported. The app suggests an account from the detected bank and categories from transaction descriptions; review and edit rows before importing. Selected expenses reduce the balance; credits and suspected duplicates are excluded by default.
- View a separate spending pie chart and download an `.xlsx` report for each bank account.
- Each browser gets its own private profile automatically. Use a short-lived one-time code to link your own devices to the same profile.
- Install the responsive app on a phone as a PWA. After opening it online once, the app stores a private snapshot on that device and supports offline bank-account creation, expense entries, and expense deletions. The installed shell can open from its cache even if the temporary tunnel returns an error while the laptop is off. Queued changes sync when the device can reach the server again; other devices will not see them before then. Removing accounts, creating categories, imports, exports, and device linking require an internet connection. Newly created offline accounts must sync before they can be removed or exported.

## Phone installation and remote testing

The local address is only reachable on the computer running the app. For a temporary cross-network demo, expose it through an HTTPS tunnel. Each person who opens the URL in their own browser gets a separate profile automatically. Anyone using the same browser profile can access its data. To use your own profile on a phone, generate a one-time code from the device that has your data and enter it on the phone within 10 minutes. Linking does not merge existing data, and a browser with existing accounts cannot be linked. If all linked browser cookies are lost, the profile cannot be recovered.

- Android: open the HTTPS URL in Chrome and choose **Install app** or **Add to Home screen**.
- iPhone: open the HTTPS URL in Safari, tap **Share**, then **Add to Home Screen**.

Offline snapshots and queued expenses are stored in that browser's local IndexedDB. They are private to the browser profile and device, and may be lost if site data is cleared. A cloud-hosted app and persistent database are required for use while the laptop is off; the included Docker image can use a PostgreSQL database through `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD`. Do not deploy the local H2 database as ephemeral container storage.

## API

- `GET /api/dashboard?period=month&date=YYYY-MM-DD`
- `GET /api/auth/csrf`, `GET /api/auth/me` (creates or restores the browser profile)
- `POST /api/auth/device-link-codes`, `POST /api/auth/device-links`
- `GET` / `POST /api/accounts`
- `DELETE /api/accounts/{accountId}` (also deletes that account's expenses)
- `GET` / `POST /api/categories`
- `GET` / `POST /api/expenses`
- `DELETE /api/expenses/{expenseId}`
- `POST /api/imports/preview` (multipart field `file`) and `POST /api/imports`
- `GET /api/accounts/{accountId}/export?period=month&date=YYYY-MM-DD`

Valid periods are `day`, `week`, `month`, and `year`. The date selects the day or the containing week, month, or year.

Statement imports are limited to 5 MB and 2,000 transactions per file. CSV, Excel, and selectable-text PDF statements should include date, description/narration, and debit or amount columns. Text statements can be delimited (comma, tab, semicolon, or pipe), use aligned columns, or group expenses under month/day headings with one description and amount per line. Month/day text headings without a year use the server's current year. Account matching uses the detected bank name; choose the account manually if there are multiple matches. Files are parsed in memory and are not retained.
