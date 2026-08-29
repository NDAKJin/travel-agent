import type {
  AgentChatResponse,
  AgentSession,
  AgentSessionDetail,
  AdminConversationDetail,
  AdminConversationSummary,
  AdminWxUser,
  AuthSession,
  PageResponse
} from "../types";

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? "").replace(/\/$/, "");
const DEBUG_LOGGING = import.meta.env.DEV || import.meta.env.VITE_DEBUG_LOGGING === "true";
const SESSION_KEY = "travel-agent-session";
export const AUTH_EXPIRED_EVENT = "travel-agent-auth-expired";
export const AUTH_UPDATED_EVENT = "travel-agent-auth-updated";
let refreshPromise: Promise<AuthSession> | null = null;

const createRequestId = () => {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
};

type RequestOptions = {
  method?: string;
  body?: unknown;
  accessToken?: string;
  allowRefresh?: boolean;
};

type AdminLoginPayload = {
  username: string;
  password: string;
};

type WxLoginPayload = {
  openId: string;
  nickname?: string;
  avatarUrl?: string;
};

export class ApiError extends Error {
  status: number;
  code?: string;

  constructor(message: string, status: number, code?: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
  }
}

const resolveUrl = (path: string) => `${API_BASE_URL}${path}`;

const emitAuthExpired = () => {
  localStorage.removeItem(SESSION_KEY);
  window.dispatchEvent(new Event(AUTH_EXPIRED_EVENT));
};

const refreshStoredSession = async (): Promise<AuthSession> => {
  let session: AuthSession | null = null;
  try {
    const raw = localStorage.getItem(SESSION_KEY);
    session = raw ? JSON.parse(raw) as AuthSession : null;
  } catch {
    session = null;
  }
  if (!session?.token.refreshToken) {
    emitAuthExpired();
    throw new ApiError("登录已过期，请重新登录", 401);
  }
  try {
    const next = await request<AuthSession>("/api/auth/refresh", {
      method: "POST",
      body: { refreshToken: session.token.refreshToken },
      allowRefresh: false
    });
    localStorage.setItem(SESSION_KEY, JSON.stringify(next));
    window.dispatchEvent(new Event(AUTH_UPDATED_EVENT));
    return next;
  } catch (error) {
    emitAuthExpired();
    throw error;
  }
};

const refreshSession = () => {
  if (!refreshPromise) {
    refreshPromise = refreshStoredSession().finally(() => { refreshPromise = null; });
  }
  return refreshPromise;
};

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = options.method ?? "GET";
  const requestId = createRequestId();
  const startedAt = performance.now();
  if (DEBUG_LOGGING) console.info("[api] request", { requestId, method, path });
  let response: Response;
  try {
    response = await fetch(resolveUrl(path), {
      method,
    headers: {
        ...(options.body instanceof FormData ? {} : { "Content-Type": "application/json" }),
        ...(options.accessToken ? { Authorization: `Bearer ${options.accessToken}` } : {})
      },
      body: options.body instanceof FormData ? options.body : options.body == null ? undefined : JSON.stringify(options.body)
    });
  } catch (error) {
    if (DEBUG_LOGGING) console.error("[api] network failure", { requestId, method, path, durationMs: Math.round(performance.now() - startedAt), error });
    throw error;
  }
  if (DEBUG_LOGGING) console.info("[api] response", {
    requestId, method, path, status: response.status,
    durationMs: Math.round(performance.now() - startedAt),
    serverRequestId: response.headers.get("X-Request-Id")
  });

  if (!response.ok) {
    const raw = await response.text();
    let message = `Request failed with status ${response.status}`;
    let code: string | undefined;
    try {
      const parsed = JSON.parse(raw) as { code?: string; message?: string };
      code = parsed.code;
      if (parsed.message) {
        message = parsed.message;
      }
    } catch {
      if (raw) {
        message = raw;
      }
    }
    if (/^refresh token is not active$/i.test(message.trim())) {
      message = "登录已过期，请重新登录";
    }
    if (DEBUG_LOGGING) console.warn("[api] request failed", { requestId, status: response.status, code, message });
    if (response.status === 401 && options.accessToken && options.allowRefresh !== false) {
      const next = await refreshSession();
      return request<T>(path, { ...options, accessToken: next.token.accessToken, allowRefresh: false });
    }
    throw new ApiError(message, response.status, code);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}

export const api = {
  loginAdmin(payload: AdminLoginPayload) {
    return request<AuthSession>("/api/auth/admin/login", {
      method: "POST",
      body: payload
    });
  },
  loginWx(payload: WxLoginPayload) {
    return request<AuthSession>("/api/auth/wx/login", {
      method: "POST",
      body: payload
    });
  },
  refresh(refreshToken: string) {
    return request<AuthSession>("/api/auth/refresh", {
      method: "POST",
      body: { refreshToken }
    });
  },
  logout(refreshToken: string) {
    return request<void>("/api/auth/logout", {
      method: "POST",
      body: { refreshToken }
    });
  },
  chat(accessToken: string, payload: { message: string; sessionId: string | null }) {
    return request<AgentChatResponse>("/api/agent/chat", {
      method: "POST",
      body: payload,
      accessToken
    });
  },
  listSessions(accessToken: string) {
    return request<AgentSession[]>("/api/agent/sessions", {
      accessToken
    });
  },
  getSession(accessToken: string, sessionId: string) {
    return request<AgentSessionDetail>(`/api/agent/sessions/${sessionId}`, {
      accessToken
    });
  },
  searchWxUsers(
    accessToken: string,
    params: { keyword?: string; page: number; size: number }
  ) {
    const query = new URLSearchParams();
    if (params.keyword) query.set("keyword", params.keyword);
    query.set("page", String(params.page));
    query.set("size", String(params.size));
    return request<PageResponse<AdminWxUser>>(`/api/admin/wx-users?${query.toString()}`, {
      accessToken
    });
  },
  listAdminSessions(accessToken: string, params: { wxUserId: number | null; page: number; size: number }) {
    const query = new URLSearchParams();
    if (params.wxUserId != null) query.set("wxUserId", String(params.wxUserId));
    query.set("page", String(params.page));
    query.set("size", String(params.size));
    return request<PageResponse<AdminConversationSummary>>(`/api/admin/sessions?${query.toString()}`, {
      accessToken
    });
  },
  getSessionDetail(accessToken: string, conversationId: number) {
    return request<AdminConversationDetail>(`/api/admin/sessions/${conversationId}`, {
      accessToken
    });
  },
  importRagDocuments(accessToken: string, files: File[]) {
    const form = new FormData();
    files.forEach(file => form.append("files", file));
    return request<import("../types").RagIngestionTask[]>(
      "/api/admin/rag/documents/import", { method: "POST", body: form, accessToken });
  },
  listRagIngestionTasks(accessToken: string) {
    return request<import("../types").RagIngestionTask[]>("/api/admin/rag/documents/import/tasks", { accessToken });
  },
  cancelRagIngestionTask(accessToken: string, taskId: number) {
    return request<void>(`/api/admin/rag/documents/import/tasks/${taskId}/cancel`, { method: "POST", accessToken });
  },
  listRagDocuments(accessToken: string, keyword = "") {
    const query = keyword ? `?keyword=${encodeURIComponent(keyword)}` : "";
    return request<import("../types").RagDocument[]>(`/api/admin/rag/documents${query}`, { accessToken });
  },
  listRagChunks(accessToken: string, params: { documentId?: number; keyword?: string; enabled?: boolean } = {}) {
    const query = new URLSearchParams();
    if (params.documentId != null) query.set("documentId", String(params.documentId));
    if (params.keyword) query.set("keyword", params.keyword);
    if (params.enabled != null) query.set("enabled", params.enabled ? "1" : "0");
    return request<import("../types").RagChunk[]>(`/api/admin/rag/chunks?${query.toString()}`, { accessToken });
  },
  toggleRagDocument(accessToken: string, documentId: number, enabled: boolean) {
    return request<void>(`/api/admin/rag/documents/${documentId}/enable?enabled=${enabled}`, {
      method: "PATCH", accessToken
    });
  },
  toggleRagChunk(accessToken: string, chunkId: number, enabled: boolean) {
    return request<void>(`/api/admin/rag/chunks/${chunkId}/enable?enabled=${enabled}`, {
      method: "PATCH", accessToken
    });
  },
  updateRagChunk(accessToken: string, chunkId: number, content: string) {
    return request<void>(`/api/admin/rag/chunks/${chunkId}`, { method: "PATCH", body: { content }, accessToken });
  },
  batchToggleRagChunks(accessToken: string, chunkIds: number[], enabled: boolean) {
    return request<void>(`/api/admin/rag/chunks/batch-enable?enabled=${enabled}`, {
      method: "PATCH", body: chunkIds, accessToken
    });
  }
};
