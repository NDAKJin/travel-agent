import { StrictMode, useEffect, useRef, useState } from "react";
import { createRoot } from "react-dom/client";
import "./style.css";
type Session = { user: { displayName: string }; token: { accessToken: string; refreshToken: string } };
type Message = { role: "user" | "assistant"; content: string };
type ItineraryItem = { day: number; period?: string; location?: string; activity?: string; transport?: string; note?: string };
type ParsedPlan = { intro: string; items: ItineraryItem[]; budget?: string; notes: string[] };
type SessionSummary = { sessionId: string; title: string; preview: string; messageCount: number };
const API = import.meta.env.VITE_API_BASE_URL ?? "";
const KEY = "agent-web-session";
class ApiError extends Error { constructor(public status: number, message: string) { super(message); } }
function clearSessionAndRedirect(): never { localStorage.removeItem(KEY); window.location.assign(window.location.pathname); throw new ApiError(401, "SESSION_EXPIRED"); }
let refreshPromise: Promise<string> | null = null;
async function refreshAccessToken(): Promise<string> {
  if (refreshPromise) return refreshPromise;
  refreshPromise = refreshAccessTokenOnce();
  try { return await refreshPromise; } finally { refreshPromise = null; }
}
async function refreshAccessTokenOnce(): Promise<string> {
  let current: Session | null = null;
  try { current = JSON.parse(localStorage.getItem(KEY) ?? "null"); } catch { current = null; }
  if (!current?.token.refreshToken) return clearSessionAndRedirect();
  const response = await fetch(`${API}/api/auth/refresh`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ refreshToken: current.token.refreshToken }) });
  if (!response.ok) return clearSessionAndRedirect();
  const payload = await response.json() as { token?: Session["token"] };
  if (!payload.token?.accessToken) return clearSessionAndRedirect();
  const token = payload.token;
  const next = { ...current, token };
  localStorage.setItem(KEY, JSON.stringify(next));
  return token.accessToken;
}
async function authorizedFetch(path: string, init: RequestInit, token?: string): Promise<Response> {
  const headers = new Headers(init.headers); if (token) headers.set("Authorization", `Bearer ${token}`);
  let response = await fetch(`${API}${path}`, { ...init, headers });
  if (response.status === 401 && token) {
    const accessToken = await refreshAccessToken(); headers.set("Authorization", `Bearer ${accessToken}`);
    response = await fetch(`${API}${path}`, { ...init, headers });
  }
  return response;
}
async function request<T>(path: string, body: unknown, token?: string): Promise<T> { const r = await authorizedFetch(path, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) }, token); if (!r.ok) { const text = await r.text(); throw new ApiError(r.status, text || `请求失败（${r.status}）`); } if (r.status === 204) return undefined as T; return r.json(); }
async function getRequest<T>(path: string, token: string): Promise<T> { const r = await authorizedFetch(path, {}, token); if (!r.ok) { const text = await r.text(); throw new ApiError(r.status, text || `请求失败（${r.status}）`); } return r.json(); }
async function deleteRequest(path: string, token: string): Promise<void> { const r = await authorizedFetch(path, { method: "DELETE" }, token); if (!r.ok) { const text = await r.text(); throw new ApiError(r.status, text || `请求失败（${r.status}）`); } }
function parsePlan(content: string): ParsedPlan | null {
  try {
    const candidate = content.match(/\{[\s\S]*"itinerary"[\s\S]*\}/)?.[0];
    if (candidate) {
      const value = JSON.parse(candidate) as { itinerary?: ItineraryItem[]; budget?: { summary?: string } | string; notes?: string[] };
      if (Array.isArray(value.itinerary) && value.itinerary.length) return { intro: content.slice(0, content.indexOf(candidate)).trim(), items: value.itinerary, budget: typeof value.budget === "string" ? value.budget : value.budget?.summary, notes: value.notes ?? [] };
    }
  } catch { /* natural language fallback */ }
  const lines = content.split(/\r?\n/).map(line => line.trim()).filter(Boolean);
  const items: ItineraryItem[] = [];
  let day = 0;
  for (const line of lines) {
    const heading = line.match(/(?:第\s*(\d+)\s*天|Day\s*(\d+))/i);
    if (heading) { day = Number(heading[1] ?? heading[2]); continue; }
    if (!day) continue;
    const match = line.replace(/^[-*•]\s*/, "").match(/^(?:(上午|中午|下午|晚上|早上|全天|夜间)\s*[:：-]?\s*)?(.*)$/);
    if (match?.[2] && match[2].length > 3) items.push({ day, period: match[1], activity: match[2] });
  }
  return items.length >= 2 ? { intro: lines.slice(0, 2).join("\n"), items, notes: [] } : null;
}

function PlanCard({ plan }: { plan: ParsedPlan }) {
  const days = [...new Set(plan.items.map(item => item.day))];
  return <div className="plan-card">{plan.intro && <p className="plan-intro">{plan.intro}</p>}<div className="plan-days">{days.map(day => <section className="plan-day" key={day}><div className="plan-day-title"><span>DAY {day}</span><b>第 {day} 天</b></div>{plan.items.filter(item => item.day === day).map((item, index) => <div className="plan-item" key={`${day}-${index}`}><span className="plan-time">{item.period || "行程"}</span><div><strong>{item.location || item.activity || "待定"}</strong>{item.location && item.activity && <p>{item.activity}</p>}{item.transport && <small>交通：{item.transport}</small>}{item.note && <small>备注：{item.note}</small>}</div></div>)}</section>)}</div>{plan.budget && <div className="plan-summary"><b>预算参考</b><span>{plan.budget}</span></div>}{plan.notes.length > 0 && <div className="plan-notes"><b>出行提醒</b>{plan.notes.map((note, index) => <span key={index}>· {note}</span>)}</div>}</div>;
}

function App() {
  const [session, setSession] = useState<Session | null>(() => { try { return JSON.parse(localStorage.getItem(KEY) ?? "null"); } catch { return null; } });
  const [mode, setMode] = useState<"login" | "register">("login"); const [phone, setPhone] = useState(""); const [email, setEmail] = useState(""); const [code, setCode] = useState(""); const [cooldown, setCooldown] = useState(0); const [tip, setTip] = useState(""); const [input, setInput] = useState(""); const [messages, setMessages] = useState<Message[]>([]); const [sessions, setSessions] = useState<SessionSummary[]>([]); const [sessionId, setSessionId] = useState<string | null>(null); const [sending, setSending] = useState(false); const sendingRef = useRef(false);
  useEffect(() => { if (!cooldown) return; const timer = window.setInterval(() => setCooldown(v => Math.max(0, v - 1)), 1000); return () => window.clearInterval(timer); }, [cooldown]);
  useEffect(() => { if (!session) return; void getRequest<SessionSummary[]>("/api/agent/sessions", session.token.accessToken).then(setSessions).catch(e => setTip(e instanceof Error ? e.message : "历史会话加载失败")); }, [session]);
  const sendCode = async () => { if (cooldown > 0) return; try { await request("/api/auth/email/send-code", { email }); setCooldown(60); setTip("验证码已发送，请查收邮箱"); } catch (e) { setTip(e instanceof Error ? e.message : "验证码发送失败"); } };
  const auth = async () => { if (mode === "register" && !/^1[3-9]\d{9}$/.test(phone)) { setTip("请输入正确的手机号"); return; } try { const next = await request<Session>(`/api/auth/email/${mode}`, { email, phone: mode === "register" ? phone : undefined, code }); localStorage.setItem(KEY, JSON.stringify(next)); setTip(""); setSession(next); } catch (e) { setTip(e instanceof Error ? e.message : "登录失败"); } };
  const chat = async () => { const message = input.trim(); if (!session || !message || sendingRef.current) return; sendingRef.current = true; setSending(true); setTip(""); setMessages(current => [...current, { role: "user", content: message }]); setInput(""); try { const result = await request<{ sessionId: string; reply: string }>("/api/agent/chat", { message, sessionId }, session.token.accessToken); setSessionId(result.sessionId); setMessages(current => [...current, { role: "assistant", content: result.reply }]); const latest = await getRequest<SessionSummary[]>("/api/agent/sessions", session.token.accessToken); setSessions(latest); } catch (e) { setTip(e instanceof Error ? e.message : "发送失败"); } finally { sendingRef.current = false; setSending(false); } };
  const selectSession = async (id: string) => { if (!session || sending) return; try { const detail = await getRequest<{ sessionId: string; messages: Message[] }>(`/api/agent/sessions/${encodeURIComponent(id)}`, session.token.accessToken); setSessionId(detail.sessionId); setMessages(detail.messages ?? []); setTip(""); } catch (e) { setTip(e instanceof Error ? e.message : "会话加载失败"); } };
  const deleteSession = async (id: string) => { if (!session || sending) return; if (!window.confirm("确定删除这个对话吗？")) return; try { await deleteRequest(`/api/agent/sessions/${encodeURIComponent(id)}`, session.token.accessToken); setSessions(current => current.filter(item => item.sessionId !== id)); if (sessionId === id) { setSessionId(null); setMessages([]); } setTip(""); } catch (e) { setTip(e instanceof Error ? e.message : "删除对话失败"); } };
  const newSession = () => { if (sending) return; setSessionId(null); setMessages([]); setTip(""); };
  if (!session) return <main className="auth-page"><section className="auth-brand"><img src="/travel-agent-icon.png" alt="行迹" /><div className="brand-label">行迹 · TRAVEL AI</div><h1>把下一站，<span>交给懂你的 AI</span></h1><p>从灵感到行程，从预算到攻略，为你轻松安排每一次出发。</p></section><section className="login"><div className="eyebrow">{mode === "login" ? "欢迎回来" : "创建账号"}</div><h2>{mode === "login" ? "登录行迹" : "注册行迹"}</h2>{mode === "register" && <input required placeholder="手机号" value={phone} onChange={e => setPhone(e.target.value)} />}<input type="email" required placeholder="邮箱地址" value={email} onChange={e => setEmail(e.target.value)} /><div className="code-row"><input required placeholder="邮箱验证码" value={code} onChange={e => setCode(e.target.value)} /><button type="button" disabled={cooldown > 0} onClick={() => void sendCode()}>{cooldown ? `${cooldown}s` : "获取验证码"}</button></div><button className="primary" onClick={() => void auth()}>{mode === "login" ? "登录" : "注册并登录"}</button><button className="switch" onClick={() => setMode(mode === "login" ? "register" : "login")}>{mode === "login" ? "还没有账号？立即注册" : "已有账号？返回登录"}</button>{tip && <small>{tip}</small>}</section></main>;
  return <main className="chat"><aside className="history"><button className="new-chat" type="button" onClick={newSession}>＋ 新建对话</button><div className="history-title">历史对话</div><div className="history-list">{sessions.map(item => <div className={`history-item ${sessionId === item.sessionId ? "active" : ""}`} key={item.sessionId}><button type="button" className="history-open" onClick={() => void selectSession(item.sessionId)}><strong>{item.title || "新对话"}</strong><span>{item.preview || "暂无消息"}</span></button><button type="button" className="history-delete" aria-label="删除对话" title="删除对话" onClick={() => void deleteSession(item.sessionId)}>×</button></div>)}</div></aside><section className="chat-main"><header><div className="chat-brand"><img src="/travel-agent-icon.png" alt="行迹" /><div><b>行迹 · TRAVEL AI</b><h1>你的旅行助手</h1></div></div><button className="logout" onClick={() => { localStorage.removeItem(KEY); setTip(""); setMessages([]); setSessions([]); setSession(null); }}>退出登录</button></header><section className="conversation">{messages.length ? messages.map((message, index) => { const plan = message.role === "assistant" ? parsePlan(message.content) : null; return <div key={`${message.role}-${index}`} className={`message ${message.role}`}><div className="message-label">{message.role === "user" ? "你" : "行迹"}</div>{plan ? <PlanCard plan={plan} /> : <div className="bubble">{message.content}</div>}</div>; }) : <div className="welcome"><h2>你好，我是行迹</h2><p>告诉我你想去哪里、什么时候出发，我来帮你规划旅程。</p></div>}{sending && <div className="message assistant thinking"><div className="message-label">行迹</div><div className="bubble">正在思考...</div></div>}</section><footer><input value={input} onChange={e => setInput(e.target.value)} placeholder="输入你的旅行需求..." onKeyDown={e => e.key === "Enter" && void chat()} /><button type="button" disabled={sending || !input.trim()} onClick={() => void chat()}>发送</button></footer>{tip && <div className="chat-tip">{tip}</div>}</section></main>;
}
createRoot(document.getElementById("root")!).render(<StrictMode><App /></StrictMode>);
