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

export type AdminAgentObservation = {
  agentName: string;
  input: string | null;
  output: string | null;
  promptTokens: number | null;
  completionTokens: number | null;
  totalTokens: number | null;
  nextDecision: string | null;
  createdAt: string;
};

export type AdminConversationMessage = {
  id: number;
  role: "user" | "assistant";
  content: string;
  observations: AdminAgentObservation[];
};

export type AdminConversationDetail = {
  sessionId: string;
  title: string;
  messages: AdminConversationMessage[];
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

export type PageResponse<T> = {
  content: T[];
  total: number;
  page: number;
  size: number;
};

export type RagDocument = {
  id: number;
  documentKey: string;
  fileName: string;
  mediaType: string | null;
  title: string;
  author: string;
  keywords: string;
  summary: string;
  questions: string;
  enabled: boolean;
  chunkCount: number;
  createdAt: string;
  updatedAt: string;
};

export type RagChunk = {
  id: number;
  documentId: number;
  documentKey: string;
  fileName: string;
  chunkIndex: number;
  startOffset: number;
  endOffset: number;
  content: string;
  keywords: string;
  summary: string;
  questions: string;
  enabled: boolean;
};

export type RagIngestionTask = {
  id: number;
  fileName: string;
  status: string;
  chunkCount: number;
  writtenCount: number;
  error: string | null;
  createdAt: string;
  updatedAt: string;
};
