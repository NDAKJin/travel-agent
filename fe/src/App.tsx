import { FormEvent, Fragment, ReactNode, useEffect, useRef, useState } from "react";
import styles from "./App.module.css";
import MapPicker from "./components/MapPicker";
import ScenicSpotsMap from "./components/ScenicSpotsMap";
import ConversationDetailPage from "./components/ConversationDetailPage";
import { api, ApiError } from "./services/api";
import type {
  AdminConversationSummary,
  AdminScenicDocumentResponse,
  AdminScenicSpot,
  AdminWxUser,
  AgentSessionDetail,
  AuthSession,
  PageResponse
} from "./types";

const STORAGE_KEY = "travel-agent-session";
const SESSION_EXPIRED = "__SESSION_EXPIRED__";
const PAGE_SIZE = 6;

type AdminView = "sessions" | "scenic" | "rag";
type Screen = "dashboard" | "detail" | "scenicDetail" | "scenicCreate";

const readStoredSession = (): AuthSession | null => {
  const raw = window.localStorage.getItem(STORAGE_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as AuthSession;
  } catch {
    window.localStorage.removeItem(STORAGE_KEY);
    return null;
  }
};

const formatTime = (value: string) => new Date(value).toLocaleString("zh-CN");

const renderInlineMarkdown = (text: string): ReactNode[] => {
  const nodes: ReactNode[] = [];
  const pattern = /(\*\*[^*]+\*\*|`[^`]+`|\[[^\]]+\]\([^)]+\)|\*[^*]+\*)/g;
  let lastIndex = 0;

  for (const match of text.matchAll(pattern)) {
    const token = match[0];
    const index = match.index ?? 0;
    if (index > lastIndex) nodes.push(text.slice(lastIndex, index));

    if (token.startsWith("**")) {
      nodes.push(<strong key={`${index}-strong`}>{token.slice(2, -2)}</strong>);
    } else if (token.startsWith("`")) {
      nodes.push(
        <code key={`${index}-code`} className={styles.markdownInlineCode}>
          {token.slice(1, -1)}
        </code>
      );
    } else if (token.startsWith("[")) {
      const linkMatch = token.match(/^\[([^\]]+)\]\(([^)]+)\)$/);
      if (linkMatch) {
        nodes.push(
          <a key={`${index}-link`} href={linkMatch[2]} target="_blank" rel="noreferrer">
            {linkMatch[1]}
          </a>
        );
      } else {
        nodes.push(token);
      }
    } else if (token.startsWith("*")) {
      nodes.push(<em key={`${index}-em`}>{token.slice(1, -1)}</em>);
    } else {
      nodes.push(token);
    }

    lastIndex = index + token.length;
  }

  if (lastIndex < text.length) nodes.push(text.slice(lastIndex));
  return nodes;
};

const renderMarkdown = (value: string): ReactNode => {
  const lines = value.replace(/\r\n/g, "\n").split("\n");
  const blocks: ReactNode[] = [];
  let buffer: string[] = [];
  let listItems: string[] = [];
  let codeLines: string[] = [];
  let inCodeBlock = false;
  let blockIndex = 0;

  const flushParagraph = () => {
    if (!buffer.length) return;
    blocks.push(
      <p key={`p-${blockIndex++}`} className={styles.markdownParagraph}>
        {renderInlineMarkdown(buffer.join(" "))}
      </p>
    );
    buffer = [];
  };

  const flushList = () => {
    if (!listItems.length) return;
    blocks.push(
      <ul key={`ul-${blockIndex++}`} className={styles.markdownList}>
        {listItems.map((item, itemIndex) => (
          <li key={`${blockIndex}-${itemIndex}`}>{renderInlineMarkdown(item)}</li>
        ))}
      </ul>
    );
    listItems = [];
  };

  const flushCode = () => {
    if (!codeLines.length) return;
    blocks.push(
      <pre key={`pre-${blockIndex++}`} className={styles.markdownCodeBlock}>
        <code>{codeLines.join("\n")}</code>
      </pre>
    );
    codeLines = [];
  };

  for (const line of lines) {
    const trimmed = line.trim();
    if (trimmed.startsWith("```")) {
      if (inCodeBlock) {
        flushCode();
        inCodeBlock = false;
      } else {
        flushParagraph();
        flushList();
        inCodeBlock = true;
      }
      continue;
    }

    if (inCodeBlock) {
      codeLines.push(line);
      continue;
    }

    if (!trimmed) {
      flushParagraph();
      flushList();
      continue;
    }

    if (trimmed.startsWith("#")) {
      flushParagraph();
      flushList();
      const text = trimmed.replace(/^#+\s*/, "");
      blocks.push(
        <h3 key={`h-${blockIndex++}`} className={styles.markdownHeading}>
          {renderInlineMarkdown(text)}
        </h3>
      );
      continue;
    }

    if (/^([-*+])\s+/.test(trimmed)) {
      flushParagraph();
      listItems.push(trimmed.replace(/^([-*+])\s+/, ""));
      continue;
    }

    if (/^>\s+/.test(trimmed)) {
      flushParagraph();
      flushList();
      blocks.push(
        <blockquote key={`quote-${blockIndex++}`} className={styles.markdownQuote}>
          {renderInlineMarkdown(trimmed.replace(/^>\s+/, ""))}
        </blockquote>
      );
      continue;
    }

    buffer.push(trimmed);
  }

  flushParagraph();
  flushList();
  flushCode();

  return <>{blocks.map((block, index) => <Fragment key={index}>{block}</Fragment>)}</>;
};

export default function App() {
  const [session, setSession] = useState<AuthSession | null>(() => readStoredSession());
  const [username, setUsername] = useState("admin");
  const [password, setPassword] = useState("admin123");
  const [loginLoading, setLoginLoading] = useState(false);
  const [loginError, setLoginError] = useState("");
  const [screen, setScreen] = useState<Screen>("dashboard");
  const [adminView, setAdminView] = useState<AdminView>("sessions");

  const [userKeywordInput, setUserKeywordInput] = useState("");
  const [userKeyword, setUserKeyword] = useState("");
  const [userPage, setUserPage] = useState(1);
  const [userPageResult, setUserPageResult] = useState<PageResponse<AdminWxUser> | null>(null);
  const [selectedUser, setSelectedUser] = useState<AdminWxUser | null>(null);
  const [userLoading, setUserLoading] = useState(false);
  const [userError, setUserError] = useState("");

  const [sessionPage, setSessionPage] = useState(1);
  const [sessionPageResult, setSessionPageResult] = useState<PageResponse<AdminConversationSummary> | null>(null);
  const [selectedConversationId, setSelectedConversationId] = useState<number | null>(null);
  const [selectedConversationDetail, setSelectedConversationDetail] = useState<AgentSessionDetail | null>(null);
  const [sessionLoading, setSessionLoading] = useState(false);
  const [sessionError, setSessionError] = useState("");
  const [detailLoading, setDetailLoading] = useState(false);

  const [docTitle, setDocTitle] = useState("");
  const [docContent, setDocContent] = useState("");
  const [docSaving, setDocSaving] = useState(false);
  const [docResult, setDocResult] = useState<AdminScenicDocumentResponse | null>(null);
  const [docError, setDocError] = useState("");
  const [scenicSpots, setScenicSpots] = useState<AdminScenicSpot[]>([]);
  const scenicLoadSequenceRef = useRef(0);
  const [scenicPage, setScenicPage] = useState(1);
  const [scenicName, setScenicName] = useState("");
  const [scenicDescription, setScenicDescription] = useState("");
  const [scenicLongitude, setScenicLongitude] = useState("");
  const [scenicLatitude, setScenicLatitude] = useState("");
  const [scenicSaving, setScenicSaving] = useState(false);
  const [scenicError, setScenicError] = useState("");
  const [selectedScenicSpot, setSelectedScenicSpot] = useState<AdminScenicSpot | null>(null);
  const [scenicEditName, setScenicEditName] = useState("");
  const [scenicEditDescription, setScenicEditDescription] = useState("");
  const [scenicEditLongitude, setScenicEditLongitude] = useState("");
  const [scenicEditLatitude, setScenicEditLatitude] = useState("");
  const [scenicEditSaving, setScenicEditSaving] = useState(false);
  const [toastMessage, setToastMessage] = useState("");

  const sessionRef = useRef<AuthSession | null>(session);
  const refreshPromiseRef = useRef<Promise<AuthSession> | null>(null);
  const toastTimerRef = useRef<number | null>(null);

  useEffect(() => {
    sessionRef.current = session;
    if (session) {
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    } else {
      window.localStorage.removeItem(STORAGE_KEY);
    }
  }, [session]);

  useEffect(() => {
    if (session && session.user.userType !== "admin") setSession(null);
  }, [session]);

  const readErrorMessage = (error: unknown, fallback: string) => (error instanceof Error ? error.message : fallback);
  const shouldRefreshAuth = (error: unknown) => error instanceof ApiError && error.status === 401 && error.code === "AUTH_ERROR";

  const showToast = (message: string) => {
    if (toastTimerRef.current !== null) window.clearTimeout(toastTimerRef.current);
    setToastMessage(message);
    toastTimerRef.current = window.setTimeout(() => {
      setToastMessage("");
      toastTimerRef.current = null;
    }, 3200);
  };

  useEffect(() => () => {
    if (toastTimerRef.current !== null) window.clearTimeout(toastTimerRef.current);
  }, []);

  useEffect(() => {
    const message = [loginError, userError, sessionError, docError, scenicError].find(Boolean);
    if (message) showToast(message);
  }, [loginError, userError, sessionError, docError, scenicError]);

  const clearAdminState = () => {
    setUserPageResult(null);
    setSelectedUser(null);
    setSessionPageResult(null);
    setSelectedConversationId(null);
    setSelectedConversationDetail(null);
    setUserError("");
    setSessionError("");
    setDocError("");
    setDocResult(null);
    setScenicSpots([]);
    setScenicError("");
    setSelectedScenicSpot(null);
    setScreen("dashboard");
    setAdminView("sessions");
  };

  const refreshSession = async (currentSession: AuthSession) => {
    if (refreshPromiseRef.current) return refreshPromiseRef.current;
    const refreshPromise = api
      .refresh(currentSession.token.refreshToken)
      .then(nextSession => {
        setSession(nextSession);
        return nextSession;
      })
      .catch(() => {
        setSession(null);
        clearAdminState();
        throw new Error(SESSION_EXPIRED);
      })
      .finally(() => {
        refreshPromiseRef.current = null;
      });
    refreshPromiseRef.current = refreshPromise;
    return refreshPromise;
  };

  const withAuthorizedRequest = async <T,>(action: (accessToken: string) => Promise<T>) => {
    const currentSession = sessionRef.current;
    if (!currentSession) throw new Error(SESSION_EXPIRED);
    try {
      return await action(currentSession.token.accessToken);
    } catch (error) {
      if (!shouldRefreshAuth(error)) throw error;
      const refreshed = await refreshSession(currentSession);
      return action(refreshed.token.accessToken);
    }
  };

  const loadUsers = async () => {
    setUserLoading(true);
    setUserError("");
    try {
      const result = await withAuthorizedRequest(accessToken =>
        api.searchWxUsers(accessToken, { keyword: userKeyword.trim(), page: userPage, size: PAGE_SIZE })
      );
      setUserPageResult(result);
    } catch (error) {
      const message = readErrorMessage(error, "加载用户列表失败");
      if (message !== SESSION_EXPIRED) setUserError(message);
    } finally {
      setUserLoading(false);
    }
  };

  const loadSessions = async () => {
    setSessionLoading(true);
    setSessionError("");
    try {
      const result = await withAuthorizedRequest(accessToken =>
        api.listAdminSessions(accessToken, { wxUserId: selectedUser?.id ?? null, page: sessionPage, size: PAGE_SIZE })
      );
      setSessionPageResult(result);
    } catch (error) {
      const message = readErrorMessage(error, "加载会话列表失败");
      if (message !== SESSION_EXPIRED) setSessionError(message);
    } finally {
      setSessionLoading(false);
    }
  };

  useEffect(() => {
    if (!session || screen !== "dashboard") return;
    void loadUsers();
  }, [session, screen, userKeyword, userPage]);

  useEffect(() => {
    if (!session || screen !== "dashboard") return;
    void loadSessions();
  }, [session, screen, selectedUser?.id, sessionPage]);

  const loadScenicSpots = async () => {
    const requestSequence = ++scenicLoadSequenceRef.current;
    setScenicError("");
    try {
      const result = await withAuthorizedRequest(accessToken => api.listScenicSpots(accessToken));
      if (requestSequence !== scenicLoadSequenceRef.current) return;
      setScenicSpots(result);
      setScenicPage(1);
    } catch (error) {
      const message = readErrorMessage(error, "加载景区列表失败");
      if (message !== SESSION_EXPIRED) setScenicError(message);
    }
  };

  useEffect(() => {
    if (session && screen === "dashboard" && adminView === "scenic") void loadScenicSpots();
  }, [session, screen, adminView]);

  useEffect(() => {
    if (screen !== "dashboard") return;
    setSelectedConversationId(null);
    setSelectedConversationDetail(null);
  }, [screen, selectedUser?.id, sessionPage]);

  const handleLogin = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setLoginLoading(true);
    setLoginError("");
    try {
      setSession(await api.loginAdmin({ username, password }));
    } catch (error) {
      setLoginError(readErrorMessage(error, "登录失败"));
    } finally {
      setLoginLoading(false);
    }
  };

  const handleLogout = async () => {
    const refreshToken = session?.token.refreshToken;
    setSession(null);
    clearAdminState();
    if (refreshToken) {
      try {
        await api.logout(refreshToken);
      } catch {}
    }
  };

  const handleSearchUsers = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setSelectedUser(null);
    setSelectedConversationId(null);
    setSelectedConversationDetail(null);
    setUserPage(1);
    setUserKeyword(userKeywordInput.trim());
  };

  const handleSelectUser = (user: AdminWxUser | null) => {
    setSelectedUser(user);
    setSessionPage(1);
    setSelectedConversationId(null);
    setSelectedConversationDetail(null);
  };

  const openConversation = async (conversationId: number) => {
    setSelectedConversationId(conversationId);
    setSelectedConversationDetail(null);
    setScreen("detail");
    setDetailLoading(true);
    setSessionError("");
    try {
      const detail = await withAuthorizedRequest(accessToken => api.getSessionDetail(accessToken, conversationId));
      setSelectedConversationDetail(detail);
    } catch (error) {
      const message = readErrorMessage(error, "加载会话详情失败");
      if (message !== SESSION_EXPIRED) setSessionError(message);
      setScreen("dashboard");
    } finally {
      setDetailLoading(false);
    }
  };

  const backToDashboard = () => setScreen("dashboard");

  const openScenicCreate = () => {
    setScenicName("");
    setScenicDescription("");
    setScenicLongitude("");
    setScenicLatitude("");
    setScenicError("");
    setScreen("scenicCreate");
  };

  const backToScenicManagement = () => {
    setAdminView("scenic");
    setScreen("dashboard");
  };

  const saveDocument = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!docTitle.trim() || !docContent.trim() || docSaving) return;
    setDocSaving(true);
    setDocError("");
    setDocResult(null);
    try {
      const result = await withAuthorizedRequest(accessToken =>
        api.addScenicDocument(accessToken, { title: docTitle.trim(), content: docContent.trim() })
      );
      setDocResult(result);
      setDocTitle("");
      setDocContent("");
    } catch (error) {
      const message = readErrorMessage(error, "保存失败");
      if (message !== SESSION_EXPIRED) setDocError(message);
    } finally {
      setDocSaving(false);
    }
  };

  const saveScenicSpot = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (scenicSaving) return;
    const longitude = Number(scenicLongitude);
    const latitude = Number(scenicLatitude);
    if (!Number.isFinite(longitude) || longitude < -180 || longitude > 180) {
      setScenicError("经度必须是 -180 到 180 之间的数字");
      return;
    }
    if (!Number.isFinite(latitude) || latitude < -90 || latitude > 90) {
      setScenicError("纬度必须是 -90 到 90 之间的数字");
      return;
    }
    setScenicSaving(true);
    setScenicError("");
    try {
      const createdSpot = await withAuthorizedRequest(accessToken => api.createScenicSpot(accessToken, {
        name: scenicName.trim(), description: scenicDescription.trim(), longitude, latitude
      }));
      scenicLoadSequenceRef.current += 1;
      setScenicSpots(current => [createdSpot, ...current.filter(spot => spot.id !== createdSpot.id)]);
      setScenicPage(1);
      setScenicName(""); setScenicDescription(""); setScenicLongitude(""); setScenicLatitude("");
      setScreen("dashboard");
      setAdminView("scenic");
      showToast("景区已创建");
    } catch (error) {
      const message = readErrorMessage(error, "保存景区失败");
      if (message !== SESSION_EXPIRED) setScenicError(message);
    } finally { setScenicSaving(false); }
  };

  const deleteScenicSpot = async (spot: AdminScenicSpot) => {
    if (!window.confirm(`确认删除${spot.name}？`)) return;
    try {
      await withAuthorizedRequest(token => api.deleteScenicSpot(token, spot.id));
      await loadScenicSpots();
    } catch (error) {
      const message = readErrorMessage(error, "删除景区失败");
      if (message !== SESSION_EXPIRED) showToast(message);
    }
  };

  const openScenicSpot = async (spot: AdminScenicSpot) => {
    try {
      const detail = await withAuthorizedRequest(accessToken => api.getScenicSpot(accessToken, spot.id));
      setSelectedScenicSpot(detail);
      setScenicEditName(detail.name);
      setScenicEditDescription(detail.description);
      setScenicEditLongitude(String(detail.longitude));
      setScenicEditLatitude(String(detail.latitude));
      setScreen("scenicDetail");
    } catch (error) {
      const message = readErrorMessage(error, "加载景区详情失败");
      if (message !== SESSION_EXPIRED) showToast(message);
    }
  };

  const saveScenicEdit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!selectedScenicSpot || scenicEditSaving) return;
    const longitude = Number(scenicEditLongitude);
    const latitude = Number(scenicEditLatitude);
    if (!Number.isFinite(longitude) || longitude < -180 || longitude > 180) {
      showToast("经度必须是 -180 到 180 之间的数字");
      return;
    }
    if (!Number.isFinite(latitude) || latitude < -90 || latitude > 90) {
      showToast("纬度必须是 -90 到 90 之间的数字");
      return;
    }
    setScenicEditSaving(true);
    try {
      const updated = await withAuthorizedRequest(accessToken => api.updateScenicSpot(accessToken, selectedScenicSpot.id, {
        name: scenicEditName.trim(),
        description: scenicEditDescription.trim(),
        longitude,
        latitude
      }));
      setSelectedScenicSpot(updated);
      await loadScenicSpots();
      showToast("景区已更新");
    } catch (error) {
      const message = readErrorMessage(error, "更新景区失败");
      if (message !== SESSION_EXPIRED) showToast(message);
    } finally {
      setScenicEditSaving(false);
    }
  };

  if (!session) {
    return (
      <main className={styles.page}>
        {toastMessage ? <div className={styles.toast} role="alert"><span className={styles.toastIcon}>!</span><span>{toastMessage}</span></div> : null}
        <section className={styles.authShell}>
          <div className={styles.heroCard}>
            <div className={styles.heroGlow} aria-hidden="true" />
            <div className={styles.heroGrid} aria-hidden="true" />
            <div className={styles.eyebrow}>管理端</div>
            <h1 className={styles.heroTitle}>景区问答与知识管理</h1>
            <p className={styles.heroLead}>把用户对话与景区知识集中在一个清晰、可维护的后台。</p>
            <div className={styles.heroBadgeRow}>
              <span className={styles.heroBadge}>会话管理</span>
              <span className={styles.heroBadge}>知识维护</span>
              <span className={styles.heroBadge}>RAG 同步</span>
            </div>
            <div className={styles.heroMetrics}>
              <article className={styles.heroMetricCard}>
                <span className={styles.heroMetricLabel}>后台能力</span>
                <strong className={styles.heroMetricValue}>2 个核心模块</strong>
              </article>
              <article className={styles.heroMetricCard}>
                <span className={styles.heroMetricLabel}>知识更新</span>
                <strong className={styles.heroMetricValue}>保存后立即生效</strong>
              </article>
            </div>
          </div>
          <section className={styles.formCard}>
            <form className={styles.form} onSubmit={handleLogin}>
              <div className={styles.formIntro}>
                <div className={styles.cardHeader}>管理员登录</div>
                <p className={styles.formIntroText}>进入后台后可查看用户会话、维护景区知识，并同步更新检索内容。</p>
              </div>
              <label className={styles.field}>
                <span>账号</span>
                <input value={username} onChange={event => setUsername(event.target.value)} placeholder="admin" />
              </label>
              <label className={styles.field}>
                <span>密码</span>
                <input
                  type="password"
                  value={password}
                  onChange={event => setPassword(event.target.value)}
                  placeholder="admin123"
                />
              </label>
              <button className={styles.primaryButton} type="submit" disabled={loginLoading}>
                {loginLoading ? "登录中..." : "进入系统"}
              </button>
            </form>
          </section>
        </section>
      </main>
    );
  }

  const userTotalPages = Math.max(1, Math.ceil((userPageResult?.total ?? 0) / PAGE_SIZE));
  const sessionTotalPages = Math.max(1, Math.ceil((sessionPageResult?.total ?? 0) / PAGE_SIZE));
  const scenicTotalPages = Math.max(1, Math.ceil(scenicSpots.length / PAGE_SIZE));
  const visibleScenicSpots = scenicSpots.slice((scenicPage - 1) * PAGE_SIZE, scenicPage * PAGE_SIZE);

  if (screen === "detail") {
    return <ConversationDetailPage
      detail={selectedConversationDetail}
      loading={detailLoading}
      onBack={backToDashboard}
      renderMarkdown={renderMarkdown}
    />;
  }

  if (selectedConversationDetail && false) {
    return (
      <main className={`${styles.page} ${styles.detailPage}`}>
        <section className={styles.scenicDetailShell}>
          <div className={styles.scenicDetailTopBar}>
            <button type="button" className={styles.backButton} onClick={backToDashboard}>
              返回
            </button>
            <div>
              <div className={styles.eyebrow}>会话详情</div>
              <h2 className={styles.sectionTitle}>{selectedConversationDetail?.title ?? "加载中..."}</h2>
            </div>
          </div>

          <div className={styles.detailPageCard}>
            {detailLoading ? <div className={styles.helperText}>正在加载会话详情...</div> : null}
            {selectedConversationDetail ? (
              <div className={styles.detailMessages}>
                {selectedConversationDetail!.messages.map((message, index) => (
                  <article
                    key={`${selectedConversationDetail!.sessionId}-${index}`}
                    className={message.role === "assistant" ? styles.assistantBubble : styles.userBubble}
                  >
                    <div className={styles.messageRole}>{message.role === "assistant" ? "AI 助手" : "用户"}</div>
                    <div className={styles.messageText}>
                      {message.role === "assistant" ? renderMarkdown(message.content) : <pre className={styles.userMessage}>{message.content}</pre>}
                    </div>
                  </article>
                ))}
              </div>
            ) : null}
          </div>
        </section>
      </main>
    );
  }

  if (screen === "scenicCreate") {
    return (
      <main className={`${styles.page} ${styles.detailPage}`}>
        {toastMessage ? <div className={styles.toast} role="alert"><span className={styles.toastIcon}>!</span><span>{toastMessage}</span></div> : null}
        <section className={styles.scenicDetailShell}>
          <div className={styles.scenicDetailTopBar}>
            <button type="button" className={styles.backButton} onClick={backToScenicManagement}>返回</button>
            <div>
              <div className={styles.eyebrow}>景区管理</div>
              <h2 className={styles.sectionTitle}>新建景区</h2>
              <p className={styles.scenicDetailLead}>填写景区信息并在地图上选择位置，保存后会同步更新搜索、问答和地理索引。</p>
            </div>
          </div>
          <form className={styles.scenicDetailCard} onSubmit={saveScenicSpot}>
            <div className={styles.scenicEditorFields}>
            <div className={styles.cardHeader}>景区基础信息</div>
            <label className={styles.field}><span>景区名</span><input value={scenicName} onChange={event => setScenicName(event.target.value)} placeholder="例如：西湖景区" /></label>
            <label className={styles.field}><span>景区介绍</span><textarea className={styles.scenicDescriptionTextarea} value={scenicDescription} onChange={event => setScenicDescription(event.target.value)} rows={8} placeholder="景区简介、亮点和游玩建议" /></label>
            </div>
              <div className={styles.editorMapPanel}>
                <div className={styles.cardHeader}>地图选点</div>
                <MapPicker
              accessToken={session?.token.accessToken ?? ""}
              longitude={scenicLongitude}
              latitude={scenicLatitude}
                onChange={(nextLongitude, nextLatitude) => {
                  setScenicLongitude(nextLongitude);
                  setScenicLatitude(nextLatitude);
                }}
                />
                <div className={styles.formRow}>
                  <label className={styles.field}><span>经度</span><input type="number" step="any" value={scenicLongitude} onChange={event => setScenicLongitude(event.target.value)} placeholder="120.1551" /></label>
                  <label className={styles.field}><span>纬度</span><input type="number" step="any" value={scenicLatitude} onChange={event => setScenicLatitude(event.target.value)} placeholder="30.2741" /></label>
                </div>
              </div>
            <div className={styles.editorFooter}>
              <div className={styles.helperText}>点击地图即可自动填入经纬度，也可以在下方手动微调。</div>
              <div className={styles.formActions}>
                <button type="button" className={styles.secondaryButton} onClick={backToScenicManagement}>取消</button>
                <button className={styles.primaryButton} type="submit" disabled={scenicSaving || !scenicName.trim() || !scenicDescription.trim() || !scenicLongitude || !scenicLatitude}>{scenicSaving ? "保存中..." : "保存景区"}</button>
              </div>
            </div>
          </form>
        </section>
      </main>
    );
  }

  if (screen === "scenicDetail" && selectedScenicSpot) {
    return (
      <main className={`${styles.page} ${styles.detailPage}`}>
        {toastMessage ? <div className={styles.toast} role="alert"><span className={styles.toastIcon}>!</span><span>{toastMessage}</span></div> : null}
        <section className={styles.detailShell}>
          <div className={styles.detailTopBar}>
            <button type="button" className={styles.backButton} onClick={() => setScreen("dashboard")}>返回</button>
            <div>
              <div className={styles.eyebrow}>景区管理</div>
              <h2 className={styles.sectionTitle}>{selectedScenicSpot.name}</h2>
              <p className={styles.scenicDetailLead}>维护景区基础信息，保存后会同步更新搜索与问答内容。</p>
            </div>
          </div>
          <form className={styles.scenicDetailCard} onSubmit={saveScenicEdit}>
            <div className={styles.scenicEditorFields}>
            <div className={styles.cardHeader}>编辑景区信息</div>
            <label className={styles.field}><span>景区名</span><input value={scenicEditName} onChange={event => setScenicEditName(event.target.value)} /></label>
            <label className={styles.field}><span>景区介绍</span><textarea className={styles.scenicDescriptionTextarea} rows={14} value={scenicEditDescription} onChange={event => setScenicEditDescription(event.target.value)} placeholder="填写景区亮点、游览建议和注意事项..." /></label>
            </div>
              <div className={styles.editorMapPanel}>
                <div className={styles.cardHeader}>地图选点</div>
                <MapPicker
              accessToken={session?.token.accessToken ?? ""}
              longitude={scenicEditLongitude}
              latitude={scenicEditLatitude}
                onChange={(nextLongitude, nextLatitude) => {
                  setScenicEditLongitude(nextLongitude);
                  setScenicEditLatitude(nextLatitude);
                }}
                />
                <div className={styles.formRow}>
                  <label className={styles.field}><span>经度</span><input type="number" step="any" value={scenicEditLongitude} onChange={event => setScenicEditLongitude(event.target.value)} /></label>
                  <label className={styles.field}><span>纬度</span><input type="number" step="any" value={scenicEditLatitude} onChange={event => setScenicEditLatitude(event.target.value)} /></label>
                </div>
              </div>
            <div className={styles.editorFooter}>
              <div className={styles.helperText}>保存后会同步更新 RAG 索引和 geo 地理索引。</div>
              <button className={styles.primaryButton} type="submit" disabled={scenicEditSaving || !scenicEditName.trim() || !scenicEditDescription.trim()}>{scenicEditSaving ? "保存中..." : "保存修改"}</button>
            </div>
          </form>
        </section>
      </main>
    );
  }

  return (
    <main className={styles.page}>
      {toastMessage ? <div className={styles.toast} role="alert"><span className={styles.toastIcon}>!</span><span>{toastMessage}</span></div> : null}
      <section className={styles.adminShell}>
        <aside className={styles.sidebar}>
          <div className={styles.sidebarHeader}>
            <div className={styles.eyebrow}>管理端</div>
          </div>

          <div className={styles.navStack}>
            <button
              type="button"
              className={adminView === "sessions" ? styles.navButtonActive : styles.navButton}
              onClick={() => setAdminView("sessions")}
            >
              <span>会话管理</span>
            </button>
            <button
              type="button"
              className={adminView === "scenic" ? styles.navButtonActive : styles.navButton}
              onClick={() => setAdminView("scenic")}
            >
              <span>景区管理</span>
            </button>
            <button
              type="button"
              className={adminView === "rag" ? styles.navButtonActive : styles.navButton}
              onClick={() => setAdminView("rag")}
            >
              <span>知识管理</span>
            </button>
          </div>

          <div className={styles.navSpacer} />

          <button type="button" className={styles.logoutBottomButton} onClick={handleLogout}>
            退出
          </button>
        </aside>

        <section className={styles.contentPanel}>
          <div className={styles.panelHeader}>
            <div>
              <div className={styles.eyebrow}>{adminView === "sessions" ? "会话管理" : adminView === "scenic" ? "景区管理" : "知识管理"}</div>
              <h2 className={styles.sectionTitle}>
                {adminView === "sessions" ? (selectedUser ? `${selectedUser.nickname} 的会话` : "全部会话") : adminView === "scenic" ? "景区地理数据与知识" : "新增景区 Markdown"}
              </h2>
            </div>
            {adminView === "sessions" && selectedUser ? <div className={styles.panelMeta}>{selectedUser.openId}</div> : null}
            {adminView === "scenic" ? <button type="button" className={styles.primaryButton} onClick={openScenicCreate}>新建景区</button> : null}
          </div>

          {adminView === "sessions" ? (
            <div className={styles.dualPanel}>
              <div className={styles.cardPanel}>
                <form className={styles.searchBar} onSubmit={handleSearchUsers}>
                  <input
                    value={userKeywordInput}
                    onChange={event => setUserKeywordInput(event.target.value)}
                    placeholder="按昵称、OpenID 或用户 ID 搜索"
                  />
                  <button className={styles.primaryButton} type="submit" disabled={userLoading}>
                    搜索
                  </button>
                  <button type="button" className={styles.secondaryButton} onClick={() => handleSelectUser(null)}>
                    重置
                  </button>
                </form>
                <div className={styles.listHeaderRow}>
                  <div className={styles.cardHeader}>用户列表</div>
                </div>
                <div className={styles.listBlock}>
                  {(userPageResult?.content ?? []).map(user => (
                    <button
                      key={user.id}
                      type="button"
                      className={selectedUser?.id === user.id ? styles.listItemActive : styles.listItem}
                      onClick={() => handleSelectUser(user)}
                    >
                      <div className={user.enabled ? styles.userStatusOn : styles.userStatusOff} />
                      <div className={styles.listTitle}>{user.nickname || "未命名用户"}</div>
                      <div className={styles.listPreview}>{user.openId}</div>
                    </button>
                  ))}
                </div>
                {userLoading ? <div className={styles.helperText}>正在加载用户...</div> : null}
                <div className={styles.paginationBar}>
                  <span className={styles.paginationMeta}>第 {userPage} / {userTotalPages} 页</span>
                  <div className={styles.paginationButtons}>
                    <button type="button" className={styles.secondaryButton} disabled={userPage <= 1} onClick={() => setUserPage(p => Math.max(1, p - 1))}>
                      上一页
                    </button>
                    <button
                      type="button"
                      className={styles.secondaryButton}
                      disabled={userPage >= userTotalPages}
                      onClick={() => setUserPage(p => Math.min(userTotalPages, p + 1))}
                    >
                      下一页
                    </button>
                  </div>
                </div>
              </div>

              <div className={styles.cardPanel}>
                <div className={styles.listHeaderRow}>
                  <div className={styles.cardHeader}>{selectedUser ? "当前用户会话" : "全部会话"}</div>
                </div>
                <div className={styles.listBlock}>
                  {(sessionPageResult?.content ?? []).map(item => (
                    <button
                      key={item.id}
                      type="button"
                      className={selectedConversationId === item.id ? styles.listItemActive : styles.listItem}
                      onClick={() => void openConversation(item.id)}
                    >
                      <div className={styles.listTitle}>{item.title}</div>
                      <div className={styles.listPreview}>{item.preview || "暂无摘要"}</div>
                      <div className={styles.listMeta}>
                        {item.messageCount} 条消息 · {formatTime(item.updatedAt)} · {item.user.displayName}
                      </div>
                    </button>
                  ))}
                </div>
                {sessionLoading ? <div className={styles.helperText}>正在加载会话...</div> : null}
                <div className={styles.paginationBar}>
                  <span className={styles.paginationMeta}>第 {sessionPage} / {sessionTotalPages} 页</span>
                  <div className={styles.paginationButtons}>
                    <button type="button" className={styles.secondaryButton} disabled={sessionPage <= 1} onClick={() => setSessionPage(p => Math.max(1, p - 1))}>
                      上一页
                    </button>
                    <button
                      type="button"
                      className={styles.secondaryButton}
                      disabled={sessionPage >= sessionTotalPages}
                      onClick={() => setSessionPage(p => Math.min(sessionTotalPages, p + 1))}
                    >
                      下一页
                    </button>
                  </div>
                </div>
              </div>
            </div>
          ) : adminView === "scenic" ? (
            <div className={styles.dualPanel}>
              <div className={styles.cardPanel}>
                <div className={styles.listHeaderRow}><div className={styles.cardHeader}>已有景区</div></div>
                <div className={styles.listBlock}>{visibleScenicSpots.map(spot => <div key={spot.id} className={styles.scenicCompactItem} role="button" tabIndex={0} onClick={() => openScenicSpot(spot)}>
                  <div className={styles.listTitle}>{spot.name}</div><div className={styles.listMeta}>经度 {spot.longitude} / 纬度 {spot.latitude}</div>
                  <button type="button" className={styles.secondaryButton} onClick={event => { event.stopPropagation(); void deleteScenicSpot(spot); }}>删除</button>
                </div>)}</div>
                <div className={styles.paginationBar}>
                  <span className={styles.paginationMeta}>第 {scenicPage} / {scenicTotalPages} 页</span>
                  <div className={styles.paginationButtons}>
                    <button type="button" className={styles.secondaryButton} disabled={scenicPage <= 1} onClick={() => setScenicPage(page => Math.max(1, page - 1))}>上一页</button>
                    <button type="button" className={styles.secondaryButton} disabled={scenicPage >= scenicTotalPages} onClick={() => setScenicPage(page => Math.min(scenicTotalPages, page + 1))}>下一页</button>
                  </div>
                </div>
              </div>
              <div className={styles.cardPanel}>
                <div className={styles.listHeaderRow}><div className={styles.cardHeader}>景区分布</div></div>
                <ScenicSpotsMap spots={scenicSpots} onSpotClick={spot => void openScenicSpot(spot)} />
              </div>
            </div>
          ) : (
            <form className={styles.editorCard} onSubmit={saveDocument}>
              <div className={styles.cardHeader}>Markdown 内容</div>
              <label className={styles.field}>
                <span>标题</span>
                <input value={docTitle} onChange={event => setDocTitle(event.target.value)} placeholder="例如：西湖景区介绍" />
              </label>
              <label className={styles.field}>
                <span>正文</span>
                <textarea
                  value={docContent}
                  onChange={event => setDocContent(event.target.value)}
                  placeholder={"# 西湖景区介绍\n\n## 简介\n...\n\n## 游玩建议\n..."}
                  rows={12}
                />
              </label>
              {docResult ? (
                <div className={styles.successBox}>
                  已保存 {docResult.fileName}
                  <br />
                  {docResult.path}
                </div>
              ) : null}
              <div className={styles.editorFooter}>
                <div className={styles.helperText}>保存后只写入 Elasticsearch RAG 索引，不会在本地保存 Markdown 文件。</div>
                <button className={styles.primaryButton} type="submit" disabled={docSaving || !docTitle.trim() || !docContent.trim()}>
                  {docSaving ? "保存中..." : "添加"}
                </button>
              </div>
            </form>
          )}
        </section>
      </section>
    </main>
  );
}
