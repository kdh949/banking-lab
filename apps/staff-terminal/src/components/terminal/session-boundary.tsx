"use client";

import { useEffect, useState } from "react";

type StaffBffSession = {
  readonly authenticated: true;
  readonly subject: string;
  readonly roles: readonly string[];
  readonly displayName: string;
  readonly expiresAt: string;
  readonly mode: string;
};

export function StaffSessionBoundary() {
  const [session, setSession] = useState<StaffBffSession | null>(null);
  const [message, setMessage] = useState("Checking opaque BFF session");

  useEffect(() => {
    void refresh();
  }, []);

  const refresh = async () => {
    try {
      const response = await fetch("/api/session", { cache: "no-store", credentials: "same-origin" });
      const value = await response.json() as StaffBffSession | { readonly authenticated: false };
      setSession(value.authenticated ? value as StaffBffSession : null);
      setMessage(value.authenticated ? "Authenticated through opaque HttpOnly BFF session" : "Sign in before API-backed work");
    } catch {
      setSession(null);
      setMessage("BFF session endpoint unavailable");
    }
  };

  const simulatedLogin = async (actorKey: string) => {
    const response = await fetch("/api/session/simulated", {
      method: "POST",
      credentials: "same-origin",
      headers: { Accept: "application/json", "Content-Type": "application/json" },
      body: JSON.stringify({ actorKey })
    });
    if (!response.ok) {
      setMessage("Dev/test simulated login is disabled; use Keycloak.");
      return;
    }
    await refresh();
  };

  const signOut = async () => {
    await fetch("/api/session", { method: "DELETE", credentials: "same-origin" });
    setSession(null);
    setMessage("Signed out");
  };

  return (
    <aside className="staff-bff-session" aria-label="직원 BFF 세션" data-testid="staff-bff-session">
      <div>
        <strong>{session ? session.displayName : "인증 필요"}</strong>
        <span>{session ? `${session.roles.join(", ")} · ${session.mode}` : message}</span>
        <small>Bearer token과 세션 credential은 브라우저 JavaScript에 노출되지 않습니다.</small>
      </div>
      <div className="staff-bff-session-actions">
        <a href="/api/session/login?returnTo=/">Keycloak 로그인</a>
        <button type="button" onClick={() => void simulatedLogin("branch-staff")}>DEV 직원</button>
        <button type="button" onClick={() => void simulatedLogin("fds-reviewer")}>DEV FDS maker</button>
        <button type="button" onClick={() => void simulatedLogin("branch-manager")}>DEV checker</button>
        <button type="button" onClick={() => void signOut()} disabled={!session}>로그아웃</button>
      </div>
    </aside>
  );
}
