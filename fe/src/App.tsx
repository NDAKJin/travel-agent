import { type FormEvent, useEffect, useState } from "react";
import ConversationDetailPage from "./components/ConversationDetailPage";
import styles from "./App.module.css";
import { api } from "./services/api";
import type { AdminConversationDetail, AdminConversationSummary, AdminWxUser, AuthSession, PageResponse } from "./types";

const STORAGE_KEY = "travel-agent-session";
const PAGE_SIZE = 20;

const storedSession = (): AuthSession | null => {
  try {
    const value = localStorage.getItem(STORAGE_KEY);
    return value ? JSON.parse(value) as AuthSession : null;
  } catch {
    localStorage.removeItem(STORAGE_KEY);
    return null;
  }
};

export default function App() {
  const [session, setSession] = useState<AuthSession | null>(storedSession);
  const [username, setUsername] = useState("admin");
  const [password, setPassword] = useState("admin123");
  const [error, setError] = useState("");
  const [users, setUsers] = useState<PageResponse<AdminWxUser> | null>(null);
  const [conversations, setConversations] = useState<PageResponse<AdminConversationSummary> | null>(null);
  const [selectedUser, setSelectedUser] = useState<number | null>(null);
  const [detail, setDetail] = useState<AdminConversationDetail | null>(null);
  const [loading, setLoading] = useState(false);

  const load = async (accessToken: string, wxUserId = selectedUser) => {
    setLoading(true);
    setError("");
    try {
      const [nextUsers, nextConversations] = await Promise.all([
        api.searchWxUsers(accessToken, { page: 1, size: PAGE_SIZE }),
        api.listAdminSessions(accessToken, { wxUserId, page: 1, size: PAGE_SIZE })
      ]);
      setUsers(nextUsers);
      setConversations(nextConversations);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "加载失败");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (session) void load(session.token.accessToken, null);
  }, [session]);

  const login = async (event: FormEvent) => {
    event.preventDefault();
    setLoading(true);
    setError("");
    try {
      const next = await api.loginAdmin({ username, password });
      localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
      setSession(next);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "登录失败");
      setLoading(false);
    }
  };

  const openDetail = async (conversationId: number) => {
    if (!session) return;
    setLoading(true);
    try {
      setDetail(await api.getSessionDetail(session.token.accessToken, conversationId));
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "加载会话失败");
    } finally {
      setLoading(false);
    }
  };

  const selectUser = (wxUserId: number | null) => {
    if (!session) return;
    setSelectedUser(wxUserId);
    void load(session.token.accessToken, wxUserId);
  };

  if (detail) {
    return <ConversationDetailPage detail={detail} loading={loading} onBack={() => setDetail(null)} renderMarkdown={(value) => <pre className={styles.userMessage}>{value}</pre>} />;
  }

  if (!session) {
    return <main className={styles.page}><section className={styles.authShell}><div className={styles.heroCard}><div className={styles.eyebrow}>LangGraph4j</div><h1 className={styles.heroTitle}>Agent 编排管理台</h1><p className={styles.heroLead}>查看多 Agent 对话、调用日志与 Token 消耗。</p></div><form className={styles.formCard} onSubmit={login}><h2 className={styles.sectionTitle}>管理员登录</h2><label className={styles.field}><span>用户名</span><input value={username} onChange={event => setUsername(event.target.value)} /></label><label className={styles.field}><span>密码</span><input type="password" value={password} onChange={event => setPassword(event.target.value)} /></label>{error ? <p className={styles.errorText}>{error}</p> : null}<button className={styles.primaryButton} disabled={loading}>{loading ? "登录中..." : "登录"}</button></form></section></main>;
  }

  return <main className={styles.page}><section className={styles.adminShell}><aside className={styles.sidebar}><div className={styles.eyebrow}>Agent Orchestration</div><h2 className={styles.sectionTitle}>会话管理</h2><button type="button" className={selectedUser === null ? styles.navButtonActive : styles.navButton} onClick={() => selectUser(null)}>全部会话</button>{users?.content.map(user => <button key={user.id} type="button" className={selectedUser === user.id ? styles.navButtonActive : styles.navButton} onClick={() => selectUser(user.id)}>{user.nickname}</button>)}<button type="button" className={styles.secondaryButton} onClick={() => { localStorage.removeItem(STORAGE_KEY); setSession(null); }}>退出登录</button></aside><section className={styles.contentPanel}><div className={styles.eyebrow}>会话与观测日志</div><h1 className={styles.sectionTitle}>{selectedUser === null ? "全部会话" : "用户会话"}</h1>{error ? <p className={styles.errorText}>{error}</p> : null}{loading ? <p className={styles.helperText}>加载中...</p> : null}<div className={styles.listBlock}>{conversations?.content.map(item => <button type="button" className={styles.listItem} key={item.id} onClick={() => void openDetail(item.id)}><strong>{item.title}</strong><span>{item.preview || "暂无消息"}</span><small>{item.messageCount} 条消息</small></button>)}</div></section></section></main>;
}
