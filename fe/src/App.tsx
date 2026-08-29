import { type FormEvent, useEffect, useMemo, useState } from "react";
import ConversationDetailPage from "./components/ConversationDetailPage";
import RagManagementPage from "./components/RagManagementPage";
import styles from "./App.module.css";
import { api, AUTH_EXPIRED_EVENT, AUTH_UPDATED_EVENT } from "./services/api";
import type { AdminConversationDetail, AdminConversationSummary, AdminWxUser, AuthSession, PageResponse } from "./types";

const STORAGE_KEY = "travel-agent-session";
const PAGE_SIZE = 50;

const storedSession = (): AuthSession | null => {
  try {
    const value = localStorage.getItem(STORAGE_KEY);
    return value ? JSON.parse(value) as AuthSession : null;
  } catch {
    localStorage.removeItem(STORAGE_KEY);
    return null;
  }
};

const formatTime = (value?: string) => value ? new Date(value).toLocaleString("zh-CN", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" }) : "-";
const today = () => new Date().toDateString();

export default function App() {
  const [session, setSession] = useState<AuthSession | null>(storedSession);
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [users, setUsers] = useState<PageResponse<AdminWxUser> | null>(null);
  const [conversations, setConversations] = useState<PageResponse<AdminConversationSummary> | null>(null);
  const [selectedUser, setSelectedUser] = useState<number | null>(null);
  const [detail, setDetail] = useState<AdminConversationDetail | null>(null);
  const [search, setSearch] = useState("");
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState<"dashboard" | "rag">("dashboard");

  useEffect(() => {
    const expire = () => {
      localStorage.removeItem(STORAGE_KEY);
      setSession(null);
      setDetail(null);
      setPage("dashboard");
      setLoading(false);
      setError("登录已过期，请重新登录");
    };
    const update = () => setSession(storedSession());
    window.addEventListener(AUTH_EXPIRED_EVENT, expire);
    window.addEventListener(AUTH_UPDATED_EVENT, update);
    return () => {
      window.removeEventListener(AUTH_EXPIRED_EVENT, expire);
      window.removeEventListener(AUTH_UPDATED_EVENT, update);
    };
  }, []);

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
      setError(reason instanceof Error ? reason.message : "加载失败，请稍后重试");
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

  const filteredConversations = useMemo(() => {
    const value = search.trim().toLowerCase();
    if (!value) return conversations?.content ?? [];
    return (conversations?.content ?? []).filter(item =>
      [item.title, item.preview, item.user?.displayName].some(text => text?.toLowerCase().includes(value)));
  }, [conversations, search]);

  const stats = useMemo(() => {
    const rows = conversations?.content ?? [];
    return {
      conversations: conversations?.total ?? 0,
      users: users?.total ?? 0,
      messages: rows.reduce((sum, item) => sum + item.messageCount, 0),
      today: rows.filter(item => new Date(item.updatedAt).toDateString() === today()).length
    };
  }, [conversations, users]);

  if (detail) return <ConversationDetailPage detail={detail} loading={loading} onBack={() => setDetail(null)} />;

  if (!session) {
    return (
      <main className={styles.loginPage}>
        <section className={styles.loginVisual}><div className={styles.brandMark}>✦</div><div className={styles.kicker}>TRAVEL AGENT / OPS</div><h1>让每一次对话，<br /><span>都清晰可见。</span></h1><p>统一查看多 Agent 会话、调用链路与运行数据。</p><div className={styles.loginVisualFooter}><span className={styles.statusDot} /> LangGraph4j workspace</div></section>
        <form className={styles.loginCard} onSubmit={login}><div className={styles.kicker}>WELCOME BACK</div><h2>登录管理平台</h2><p className={styles.muted}>使用管理员账号进入运营看板</p><label className={styles.field}><span>用户名</span><input value={username} onChange={event => setUsername(event.target.value)} autoComplete="username" /></label><label className={styles.field}><span>密码</span><input type="password" value={password} onChange={event => setPassword(event.target.value)} autoComplete="current-password" /></label>{error ? <p className={styles.error}>{error}</p> : null}<button className={styles.primaryButton} disabled={loading}>{loading ? "登录中..." : "进入平台 →"}</button></form>
      </main>
    );
  }

  if (String(page) === "rag") return <RagManagementPage session={session} onBack={() => setPage("dashboard")} onNavigate={setPage} />;

  return (
    <main className={styles.appPage}>
      <aside className={styles.sidebar}><div className={styles.logo}><span>✦</span><div>TRAVEL<br /><small>AGENT OPS</small></div></div><div className={styles.sidebarLabel}>WORKSPACE</div><button className={page === "dashboard" ? styles.navActive : styles.navButton} type="button" onClick={() => setPage("dashboard")}>▦ <span>运营看板</span></button><button className={selectedUser === null && page === "dashboard" ? styles.navActive : styles.navButton} type="button" onClick={() => { setPage("dashboard"); selectUser(null); }}>◷ <span>全部会话</span></button><button className={page === "rag" ? styles.navActive : styles.navButton} type="button" onClick={() => setPage("rag")}>⌁ <span>旅行知识库</span></button><div className={styles.sidebarLabel}>用户筛选</div><div className={styles.userNav}>{users?.content.slice(0, 8).map(user => <button key={user.id} className={selectedUser === user.id ? styles.userActive : styles.userButton} type="button" onClick={() => selectUser(user.id)}><span className={styles.avatar}>{(user.nickname || "用").slice(0, 1)}</span>{user.nickname || "未命名用户"}</button>)}</div><div className={styles.sidebarBottom}><div className={styles.account}><span className={styles.avatar}>A</span><div><strong>{session.user.displayName || "管理员"}</strong><small>Administrator</small></div></div><button type="button" className={styles.logout} onClick={() => { localStorage.removeItem(STORAGE_KEY); setSession(null); }}>退出登录</button></div></aside>
      <section className={styles.mainPanel}><header className={styles.topbar}><div><div className={styles.kicker}>OVERVIEW / REAL-TIME</div><h1>运营看板</h1><p className={styles.muted}>了解你的 AI 旅行助手正在发生什么。</p></div><div className={styles.topbarActions}><span className={styles.liveBadge}><span className={styles.statusDot} /> 系统运行中</span><button className={styles.refreshButton} type="button" onClick={() => void load(session.token.accessToken)}>↻ 刷新</button></div></header>{error ? <div className={styles.errorBanner}>{error}</div> : null}<section className={styles.metricGrid}><Metric label="会话总数" value={stats.conversations} hint="累计创建的对话" icon="◌" /><Metric label="用户总数" value={stats.users} hint="已接入的微信用户" icon="♙" /><Metric label="消息总量" value={stats.messages} hint="当前页会话统计" icon="↗" /><Metric label="今日更新" value={stats.today} hint="今天有新消息的会话" icon="✧" accent /></section><section className={styles.dashboardGrid}><div className={styles.panelCard}><div className={styles.panelHeading}><div><h2>最近会话</h2><p className={styles.muted}>点击查看完整对话与 Agent 调用日志</p></div><span className={styles.countPill}>{filteredConversations.length} 条</span></div><div className={styles.toolbar}><div className={styles.search}><span>⌕</span><input placeholder="搜索标题、内容或用户" value={search} onChange={event => setSearch(event.target.value)} /></div><span className={styles.muted}>{loading ? "同步中..." : `更新于 ${new Date().toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit" })}`}</span></div><div className={styles.conversationList}>{filteredConversations.map(item => <button className={styles.conversationRow} type="button" key={item.id} onClick={() => void openDetail(item.id)}><span className={styles.conversationIcon}>✦</span><span className={styles.conversationBody}><strong>{item.title || "未命名会话"}</strong><span>{item.preview || "暂无消息"}</span><small>{item.user?.displayName || "匿名用户"} · {item.messageCount} 条消息</small></span><span className={styles.conversationTime}>{formatTime(item.updatedAt)}<b>›</b></span></button>)}{!filteredConversations.length ? <div className={styles.empty}>没有找到符合条件的会话</div> : null}</div></div><div className={styles.sideStack}><button className={styles.ragSidebarButton} type="button" onClick={() => setPage("rag")}><span className={styles.ragSidebarIcon}>⌁</span><span><strong>旅行知识库</strong><small>管理文档与向量内容</small></span><b>→</b></button><div className={styles.panelCard}><div className={styles.panelHeading}><div><h2>用户概览</h2><p className={styles.muted}>最近接入的用户</p></div><span className={styles.countPill}>{users?.content.length ?? 0}</span></div><div className={styles.userList}>{users?.content.slice(0, 6).map(user => <div className={styles.userRow} key={user.id}><span className={styles.avatar}>{(user.nickname || "用").slice(0, 1)}</span><span><strong>{user.nickname || "未命名用户"}</strong><small>{user.enabled ? "账号正常" : "已停用"}</small></span><i className={user.enabled ? styles.online : styles.offline} /></div>)}</div></div><div className={`${styles.panelCard} ${styles.signalCard}`}><div className={styles.kicker}>PLATFORM SIGNAL</div><h2>多 Agent 协同</h2><p>从意图识别到路线审核，每一条链路都可追踪。</p><div className={styles.signalLine}><span style={{ width: "82%" }} /></div><small>观测数据由 Kafka 异步写入</small></div></div></section></section>
    </main>
  );
}

function Metric({ label, value, hint, icon, accent = false }: { label: string; value: number; hint: string; icon: string; accent?: boolean }) {
  return <article className={`${styles.metricCard} ${accent ? styles.metricAccent : ""}`}><span className={styles.metricIcon}>{icon}</span><div><p>{label}</p><strong>{value.toLocaleString()}</strong><small>{hint}</small></div></article>;
}
