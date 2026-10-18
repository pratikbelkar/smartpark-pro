import axios, { AxiosError } from "axios";
import type { ApiEnvelope } from "../types";

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "http://localhost:8080",
  timeout: 15000,
});

let accessToken: string | null = sessionStorage.getItem("sp_access");
let refreshToken: string | null = localStorage.getItem("sp_refresh");

export function setTokens(access: string | null, refresh: string | null) {
  accessToken = access;
  refreshToken = refresh;
  if (access) sessionStorage.setItem("sp_access", access);
  else sessionStorage.removeItem("sp_access");
  if (refresh) localStorage.setItem("sp_refresh", refresh);
  else localStorage.removeItem("sp_refresh");
}

export function getRefreshToken() {
  return refreshToken;
}

api.interceptors.request.use((config) => {
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`;
  }
  return config;
});

let refreshing: Promise<string | null> | null = null;

async function tryRefresh(): Promise<string | null> {
  if (!refreshToken) return null;
  if (!refreshing) {
    refreshing = axios
      .post(`${api.defaults.baseURL}/api/v1/auth/refresh`, { refreshToken })
      .then((res) => {
        const tokens = res.data.data.tokens as { accessToken: string; refreshToken: string };
        setTokens(tokens.accessToken, tokens.refreshToken);
        return tokens.accessToken;
      })
      .catch(() => {
        setTokens(null, null);
        return null;
      })
      .finally(() => {
        refreshing = null;
      });
  }
  return refreshing;
}

api.interceptors.response.use(
  (res) => res,
  async (error: AxiosError) => {
    const original = error.config as (typeof error.config & { _retried?: boolean }) | undefined;
    if (error.response?.status === 401 && original && !original._retried && refreshToken) {
      original._retried = true;
      const next = await tryRefresh();
      if (next) {
        original.headers.Authorization = `Bearer ${next}`;
        return api(original);
      }
    }
    return Promise.reject(error);
  },
);

export function apiErrorMessage(error: unknown, fallback = "Something went wrong."): string {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as ApiEnvelope<unknown> | undefined;
    if (data?.message) return data.message;
    if (error.code === "ECONNABORTED") return "Request timed out. Check your connection.";
    if (!error.response) return "Cannot reach the server. Is the backend running?";
  }
  return fallback;
}

export default api;
