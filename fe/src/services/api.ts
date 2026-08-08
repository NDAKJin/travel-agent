import type {
  AgentChatResponse,
  AgentSession,
  AgentSessionDetail,
  AdminConversationSummary,
  AdminScenicDocumentResponse,
  AdminScenicSpot,
  AdminWxUser,
  AuthSession,
  PageResponse
} from "../types";

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? "").replace(/\/$/, "");
const DEBUG_LOGGING = import.meta.env.DEV || import.meta.env.VITE_DEBUG_LOGGING === "true";

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
      "Content-Type": "application/json",
      ...(options.accessToken ? { Authorization: `Bearer ${options.accessToken}` } : {})
      },
      body: options.body == null ? undefined : JSON.stringify(options.body)
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
    if (DEBUG_LOGGING) console.warn("[api] request failed", { requestId, status: response.status, code, message });
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
    return request<AgentSessionDetail>(`/api/admin/sessions/${conversationId}`, {
      accessToken
    });
  },
  addScenicDocument(accessToken: string, payload: { title: string; content: string }) {
    return request<AdminScenicDocumentResponse>("/api/admin/rag/scenic-documents", {
      method: "POST",
      body: payload,
      accessToken
    });
  },
  listScenicSpots(accessToken: string) {
    return request<AdminScenicSpot[]>("/api/admin/scenic-spots", { accessToken });
  },
  createScenicSpot(accessToken: string, payload: { name: string; description: string; longitude: number; latitude: number }) {
    return request<AdminScenicSpot>("/api/admin/scenic-spots", {
      method: "POST",
      body: payload,
      accessToken
    });
  },
  updateScenicSpot(accessToken: string, id: string, payload: { name: string; description: string; longitude: number; latitude: number }) {
    return request<AdminScenicSpot>(`/api/admin/scenic-spots/${id}`, {
      method: "PUT",
      body: payload,
      accessToken
    });
  },
  deleteScenicSpot(accessToken: string, id: string) {
    return request<void>(`/api/admin/scenic-spots/${id}`, {
      method: "DELETE",
      accessToken
    });
  }
};
