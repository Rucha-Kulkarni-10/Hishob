# HISHOB Web — React Frontend

React + Vite + TypeScript frontend for the HISHOB shared-expense platform.

## Requirements

- Node.js 20+
- npm 10+

## Setup

```bash
# From this directory
cp .env.example .env
npm install
npm run dev
```

Application starts at: **http://localhost:5173**

All API requests are proxied through the HISHOB API Gateway at **http://localhost:8080**.

## Environment Variables

| Variable | Description | Default |
|---|---|---|
| `VITE_API_BASE_URL` | HISHOB API Gateway URL | `http://localhost:8080` |

**Important:** `VITE_*` variables are bundled into the browser build. Never put secrets, JWT keys, database passwords, or internal service tokens into environment variables.

## Scripts

| Command | Description |
|---|---|
| `npm run dev` | Start development server on port 5173 |
| `npm run build` | TypeScript check + production build |
| `npm run lint` | Run ESLint |
| `npm run preview` | Preview production build locally |

## Backend Services Required

Start these services before using the frontend:

1. Eureka Discovery Server → :8761
2. Config Server → :8888
3. API Gateway → :8080 (**the only backend port the frontend calls**)
4. Auth Service → :8081

## Architecture

```
Browser
  ↓
React Frontend (localhost:5173)
  ↓
API Gateway (localhost:8080)
  ↓
Microservices (8081–8085)
```

The frontend never calls microservices directly. All requests go through the Gateway.
