import { StrictMode, useState } from "react";
import { createRoot } from "react-dom/client";
import "./style.css";

const API = import.meta.env.VITE_API_BASE_URL ?? "";
async function post<T>(path: string, body: unknown): Promise<T> {
  const response = await fetch(`${API}${path}`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(await response.text());
  if (response.status === 204) return undefined as T;
  return response.json();
}

function App() {
  const [mode, setMode] = useState<"login" | "register">("login");
  const [phone, setPhone] = useState(""); const [email, setEmail] = useState(""); const [code, setCode] = useState("");
  const [logged, setLogged] = useState(false); const [input, setInput] = useState(""); const [reply, setReply] = useState(""); const [sessionId, setSessionId] = useState<string | null>(null); const [tip, setTip] = useState("");
  const sendCode = async () => { try { await post("/api/auth/email/send-code", { email }); setTip("验证码已发送，请查收邮箱"); } catch (error) { setTip(error instanceof Error ? error.message : "验证码发送失败"); } };
  const auth = async () => { if (mode === "register" && !/^1[3-9]\d{9}$/.test(phone)) { setTip("请输入正确的手机号"); return; } try { const result = await post<{ valid: boolean }>("/api/auth/email/verify-code", { email, code }); if (result.valid) setLogged(true); else setTip("验证码错误或已过期"); } catch (error) { setTip(error instanceof Error ? error.message : "登录失败"); } };
  const chat = async () => { if (!input.trim()) return; try { const result = await post<{ sessionId: string; reply: string }>("/api/agent/chat", { message: input, sessionId }); setSessionId(result.sessionId); setReply(result.reply); setInput(""); } catch (error) { setTip(error instanceof Error ? error.message : "发送失败"); } };
  if (!logged) return <main className="auth-page"><section className="auth-brand"><img src="/travel-agent-icon.png" alt="行迹" /><div className="brand-label">行迹 · TRAVEL AI</div><h1>把下一站，<span>交给懂你的 AI</span></h1><p>从灵感到行程，从预算到攻略，为你轻松安排每一次出发。</p><div className="route-dots">● ━━━ ● ━━ ●</div></section><section className="login"><div className="eyebrow">欢迎回来</div><h2>{mode === "login" ? "登录行迹" : "创建账号"}</h2><p className="subheading">{mode === "login" ? "登录后继续你的旅行计划" : "手机号是你的唯一账号凭证"}</p>{mode === "register" && <input required placeholder="手机号" value={phone} onChange={e => setPhone(e.target.value)} /> }<input type="email" required placeholder="邮箱地址" value={email} onChange={e => setEmail(e.target.value)} /><div className="code-row"><input required placeholder="邮箱验证码" value={code} onChange={e => setCode(e.target.value)} /><button type="button" onClick={() => void sendCode()}>获取验证码</button></div><button className="primary" onClick={() => void auth()}>{mode === "login" ? "登录" : "注册并登录"}</button><button className="switch" onClick={() => { setMode(mode === "login" ? "register" : "login"); setTip(""); }}>{mode === "login" ? "还没有账号？立即注册" : "已有账号？返回登录"}</button>{tip && <small>{tip}</small>}</section></main>;
  return <main className="chat"><header><div className="chat-brand"><img src="/travel-agent-icon.png" alt="行迹" /><div><b>行迹 · TRAVEL AI</b><h1>你的旅行助手</h1></div></div><span className="online">● 在线</span></header><section className="conversation">{reply ? <div className="bubble">{reply}</div> : <div className="welcome"><h2>你好，我是行迹</h2><p>告诉我你想去哪里、什么时候出发，我来帮你规划一段合适的旅程。</p><div className="suggestions"><button onClick={() => setInput("帮我规划一次周末短途旅行")}>周末短途旅行</button><button onClick={() => setInput("推荐一个适合亲子游的目的地")}>亲子旅行推荐</button><button onClick={() => setInput("帮我做一份预算友好的旅行计划")}>预算友好行程</button></div></div>}</section><footer><input value={input} onChange={e => setInput(e.target.value)} placeholder="输入你的旅行需求..." onKeyDown={e => e.key === "Enter" && void chat()} /><button onClick={() => void chat()}>发送</button></footer></main>;
}
createRoot(document.getElementById("root")!).render(<StrictMode><App /></StrictMode>);
