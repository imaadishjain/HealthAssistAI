# HealthAssist AI — React Frontend

A **React (JavaScript) + Tailwind CSS** conversion of the original Angular `HealthAssistFrontend` project.

The UI design, colors, animations, layout, and business logic are preserved 1:1.

## Prerequisites

- Node.js 18+ (LTS recommended)
- npm 9+

## Install

```bash
npm install
```

### Troubleshooting: 401 / corporate registry

If `npm install` fails with **401 Unauthorized** or a redirect to an internal registry, force the public npm registry for this project:

```bash
# Option 1 – one-off install
npm install --registry=https://registry.npmjs.org/

# Option 2 – project-level .npmrc (create this file next to package.json)
echo registry=https://registry.npmjs.org/ > .npmrc
npm install
```

If your machine is fully offline, download the following packages on a permitted network and copy them into `node_modules/` before running `npm run dev`:
`react`, `react-dom`, `react-router-dom`, `axios`, `vite`, `@vitejs/plugin-react`, `tailwindcss`, `postcss`, `autoprefixer`.

## Run (dev)

```bash
npm run dev
```

The app starts at `http://localhost:4200` (matches the original Angular port).

## Build

```bash
npm run build
```

## Backend

This app expects the Spring Boot backend on `http://localhost:8080` (same as the Angular version). No CORS configuration changes are needed — the axios base URL in [src/services/api.js](src/services/api.js) points to `http://localhost:8080`.

## Structure

```
src/
├── assets/            # logo.svg
├── components/        # Reusable layout: Navbar, Footer
├── context/           # ChatContext (persists chat state across nav)
├── pages/             # Dashboard, Chat, DoctorList, Appointments, Tickets, Audit
├── services/          # axios API service modules
├── utils/             # format helpers (date, number)
├── App.jsx            # Router + shell
├── main.jsx           # Entry point
└── index.css          # Design tokens, Tailwind, base styles
```

## Angular → React mapping

| Angular concept                      | React equivalent                          |
| ------------------------------------ | ----------------------------------------- |
| `NgModule` (`app.module.ts`)         | Composition in [main.jsx](src/main.jsx)   |
| Router (`app-routing.module.ts`)     | `<Routes>` in [App.jsx](src/App.jsx)      |
| `routerLink` / `routerLinkActive`    | `<NavLink>` from `react-router-dom`       |
| Component `styleUrls`                | Per-file `.css` import next to `.jsx`     |
| `HttpClient` + `Observable`          | axios + `Promise` in [services/](src/services/) |
| Singleton `@Injectable` service      | React Context ([ChatContext](src/context/ChatContext.jsx)) |
| `[(ngModel)]`                        | Controlled inputs (`value` + `onChange`)  |
| `*ngIf`                              | `{condition && (<JSX />)}` ternary        |
| `*ngFor="let x of xs"`               | `xs.map(x => <JSX key={...} />)`          |
| `[class.foo]="cond"`                 | `` className={`foo ${cond ? 'on' : ''}`} `` |
| `(click)="fn()"`                     | `onClick={() => fn()}`                    |
| Pipe `\| date`                       | [`formatDate`](src/utils/format.js) util  |
| `EventEmitter` / `@Output`           | Callback props                            |

## Route table

| Path             | Component                              |
| ---------------- | -------------------------------------- |
| `/`              | [Dashboard](src/pages/Dashboard.jsx)   |
| `/chat`          | [Chat](src/pages/Chat.jsx)             |
| `/doctors`       | [DoctorList](src/pages/DoctorList.jsx) |
| `/appointments`  | [Appointments](src/pages/Appointments.jsx) |
| `/tickets`       | [Tickets](src/pages/Tickets.jsx)       |
| `/audit`         | [Audit](src/pages/Audit.jsx)           |
| `*`              | redirect → `/`                         |

## Notes on parity

- Pure JavaScript — no TypeScript, no build-time type-checking. Uses Vite's fast JSX transform via `@vitejs/plugin-react`.
- All dark-theme design tokens (colors, gradients, shadows, radii) from the original `src/styles.css` are preserved in [src/index.css](src/index.css).
- Chat history persists to `localStorage` under the key `healthassist_chat_history` — identical behaviour to the Angular singleton `ChatService`.
- The `/audit` page reads `?workflowId=` from the URL to auto-select a workflow (used when the Chat page links to it).
- Streaming chat uses `EventSource` (SSE) — same endpoint as the Angular version.
- [jsconfig.json](jsconfig.json) gives VS Code IntelliSense (imports, JSX) without adding a TypeScript toolchain.
