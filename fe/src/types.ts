export type LoginMode = "admin" | "wx";

export type AuthUser = {
  id: number;
  userType: string;
  subject: string;
  displayName: string;
};

export type AuthTokens = {
  accessToken: string;
  accessTokenExpiresAt: string;
  refreshToken: string;
  refreshTokenExpiresAt: string;
};

export type AuthSession = {
  user: AuthUser;
  token: AuthTokens;
};

export type AgentMessage = {
  id: string;
  role: "user" | "assistant";
  text: string;
  createdAt: string;
  meta?: string;
};

export type AgentChatResponse = {
  sessionId: string;
  reply: string;
  agentName: string;
  model: string;
  toolEnabled: boolean;
};

export type AgentSession = {
  sessionId: string;
  title: string;
  preview: string;
  messageCount: number;
  updatedAt: string;
};

export type AgentConversationMessage = {
  role: "user" | "assistant";
  content: string;
};

export type AgentSessionDetail = {
  sessionId: string;
  title: string;
  messages: AgentConversationMessage[];
  createdAt: string;
  updatedAt: string;
};

export type AdminWxUser = {
  id: number;
  openId: string;
  nickname: string;
  enabled: boolean;
  updatedAt: string;
};

export type AdminConversationUser = {
  id: number;
  userType: string;
  subject: string;
  displayName: string;
};

export type AdminConversationSummary = {
  id: number;
  sessionId: string;
  title: string;
  preview: string;
  messageCount: number;
  updatedAt: string;
  user: AdminConversationUser;
};

export type AdminScenicDocumentResponse = {
  fileName: string;
  path: string;
  updatedAt: string;
};

export type AdminScenicSpot = {
  id: string;
  name: string;
  description: string;
  longitude: number;
  latitude: number;
  updatedAt: string;
};

export type PageResponse<T> = {
  content: T[];
  total: number;
  page: number;
  size: number;
};
