/**
 * Centralized Axios API client.
 *
 * All HTTP requests to the HISHOB backend go through this instance.
 * The base URL is read from the VITE_API_BASE_URL environment variable,
 * which must point to the API Gateway (local dev default: http://localhost:8080,
 * start-hishob.ps1 runs the Gateway on http://localhost:8087; production sets
 * VITE_API_BASE_URL to the public Gateway URL at build time).
 *
 * Interceptors:
 *   Request  — attaches the stored JWT as a Bearer token if present.
 *   Response — on 401, clears the stored token so stale JWTs are removed.
 *              Does NOT auto-redirect (the AuthContext handles routing).
 *
 * The frontend never calls microservices directly.
 * Every request goes through the Gateway.
 */

import axios from 'axios';
import { getToken, removeToken } from '@/utils/tokenStorage';

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL as string | undefined;

if (!apiBaseUrl) {
  console.error(
    '[HISHOB] VITE_API_BASE_URL is not defined. ' +
      'Copy .env.example to .env and set the Gateway URL.'
  );
}

const api = axios.create({
  baseURL: apiBaseUrl ?? 'http://localhost:8087',
  headers: {
    'Content-Type': 'application/json',
  },
});

// ── Request interceptor: attach JWT ──────────────────────────────────────────
api.interceptors.request.use((config) => {
  const token = getToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// ── Response interceptor: handle 401 ─────────────────────────────────────────
api.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      // Remove any stale token. The AuthContext listener will pick up the
      // token removal and update the UI state on the next render cycle.
      removeToken();
    }
    return Promise.reject(error);
  }
);

export default api;
