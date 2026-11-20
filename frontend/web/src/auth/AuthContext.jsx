// Step 37 - Context API for authentication state.
// Any component can call useAuth() instead of passing user/login/logout down through props.
import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { claims, refreshSession, tokens } from "../api/client";
import { Auth, Users } from "../api/endpoints";

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);          // profile of the signed-in user
  const [status, setStatus] = useState("loading"); // loading | authenticated | anonymous

  const loadProfile = useCallback(async () => {
    const me = await Users.me();
    setUser({ ...me, roles: claims()?.roles ?? [] });
    setStatus("authenticated");
  }, []);

  // On start-up: accept a session handed over by the landing page, then try to resume it.
  useEffect(() => {
    tokens.onSessionEnded(() => { setUser(null); setStatus("anonymous"); });
    const handoff = new URLSearchParams(window.location.hash.slice(1)).get("handoff");
    if (handoff) {
      tokens.set(null, handoff);
      window.history.replaceState(null, "", window.location.pathname + window.location.search); // drop it from the URL
    }
    if (!tokens.refresh) { setStatus("anonymous"); return; }
    refreshSession().then(loadProfile).catch(() => setStatus("anonymous"));
  }, [loadProfile]);

  const login = useCallback(async (emailOrUsername, password) => {
    const t = await Auth.login(emailOrUsername, password);
    tokens.set(t.accessToken, t.refreshToken);
    await loadProfile();
  }, [loadProfile]);

  const register = useCallback(async (email, username, password) => {
    await Auth.register(email, username, password);
    await login(username, password);
  }, [login]);

  const logout = useCallback(async () => {
    const refresh = tokens.refresh;
    tokens.clear();
    setUser(null);
    setStatus("anonymous");
    if (refresh) await Auth.logout(refresh).catch(() => {});
  }, []);

  const value = useMemo(() => ({
    user, status, login, register, logout, reloadProfile: loadProfile,
    isAdmin: !!user?.roles?.includes("ADMIN"),
  }), [user, status, login, register, logout, loadProfile]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used inside <AuthProvider>");
  return ctx;
}
