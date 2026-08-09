import type { ReactNode } from "react";
import styles from "../App.module.css";
import type { AgentSessionDetail } from "../types";

type ConversationDetailPageProps = {
  detail: AgentSessionDetail | null;
  loading: boolean;
  onBack: () => void;
  renderMarkdown: (value: string) => ReactNode;
};

export default function ConversationDetailPage({ detail, loading, onBack, renderMarkdown }: ConversationDetailPageProps) {
  return (
    <main className={`${styles.page} ${styles.detailPage}`}>
      <section className={styles.scenicDetailShell}>
        <div className={styles.scenicDetailTopBar}>
          <button type="button" className={styles.backButton} onClick={onBack}>返回</button>
          <div>
            <div className={styles.eyebrow}>会话详情</div>
            <h2 className={styles.sectionTitle}>{detail?.title ?? "加载中..."}</h2>
          </div>
        </div>
        <div className={styles.detailPageCard}>
          {loading ? <div className={styles.helperText}>正在加载会话详情...</div> : null}
          {detail ? (
            <div className={styles.detailMessages}>
              {detail.messages.map((message, index) => (
                <article
                  key={`${detail.sessionId}-${index}`}
                  className={message.role === "assistant" ? styles.assistantBubble : styles.userBubble}
                >
                  <div className={styles.messageRole}>{message.role === "assistant" ? "AI 助手" : "用户"}</div>
                  <div className={styles.messageText}>
                    {message.role === "assistant" ? renderMarkdown(message.content) : (
                      <pre className={styles.userMessage}>{message.content}</pre>
                    )}
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
