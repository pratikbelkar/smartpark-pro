import { createContext, useCallback, useContext, useEffect, useState } from "react";
import type { ReactNode } from "react";
import api, { getRefreshToken, setTokens } from "./api";
import type { User } from "../types";

interface AuthState {
  user: User | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (fullName: string, email: string, password: string, phone?: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!getRefreshToken()) {
      setLoading(false);
      return;
    }
    api
      .get("/api/v1/auth/me")
      .then((res) => setUser(res.data.data as User))
      .catch(() => setTokens(null, null))
      .finally(() => setLoading(false));
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const res = await api.post("/api/v1/auth/login", { email, password });
    const { tokens, user: u } = res.data.data as {
      tokens: { accessToken: string; refreshToken: string };
      user: User;
    };
    setTokens(tokens.accessToken, tokens.refreshToken);
    setUser(u);
  }, []);

  const register = useCallback(async (fullName: string, email: string, password: string, phone?: string) => {
    const res = await api.post("/api/v1/auth/register", { fullName, email, password, phone });
    const { tokens, user: u } = res.data.data as {
      tokens: { accessToken: string; refreshToken: string };
      user: User;
    };
    setTokens(tokens.accessToken, tokens.refreshToken);
    setUser(u);
  }, []);

  const logout = useCallback(async () => {
    const refresh = getRefreshToken();
    try {
      if (refresh) await api.post("/api/v1/auth/logout", { refreshToken: refresh });
    } finally {
      setTokens(null, null);
      setUser(null);
    }
  }, []);

  return <AuthContext.Provider value={{ user, loading, login, register, logout }}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used inside AuthProvider");
  return ctx;
}
