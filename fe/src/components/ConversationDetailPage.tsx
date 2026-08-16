import { useState, type ReactNode } from "react";
import styles from "../App.module.css";
import type { AdminConversationDetail, AdminConversationMessage } from "../types";

type ConversationDetailPageProps = {
  detail: AdminConversationDetail | null;
  loading: boolean;
  onBack: () => void;
  renderMarkdown: (value: string) => ReactNode;
};

export default function ConversationDetailPage({ detail, loading, onBack, renderMarkdown }: ConversationDetailPageProps) {
  const [logMessage, setLogMessage] = useState<AdminConversationMessage | null>(null);

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
                  {message.observations.length > 0 ? (
                    <button type="button" className={styles.secondaryButton} onClick={() => setLogMessage(message)}>
                      查看对话日志（{message.observations.length} 条）
                    </button>
                  ) : null}
                </article>
              ))}
            </div>
          ) : null}
        </div>
      </section>
      {logMessage ? (
        <div className={styles.scenicModalBackdrop} role="presentation" onMouseDown={(event) => {
          if (event.target === event.currentTarget) setLogMessage(null);
        }}>
          <section className={`${styles.scenicModal} ${styles.observationModal}`} role="dialog" aria-modal="true" aria-label="对话日志">
            <div className={styles.scenicModalHeader}>
              <div>
                <div className={styles.eyebrow}>Agent 观测</div>
                <h2 className={styles.sectionTitle}>对话日志</h2>
              </div>
              <button type="button" className={styles.secondaryButton} onClick={() => setLogMessage(null)}>关闭</button>
            </div>
            <div className={styles.observationModalMessage}>
              <div className={styles.messageRole}>{logMessage.role === "assistant" ? "AI 助手" : "用户"}</div>
              <div className={styles.messageText}>{logMessage.content}</div>
            </div>
            <div className={styles.observationLogList}>
              {logMessage.observations.map((observation, observationIndex) => (
                <article key={`${logMessage.id}-${observationIndex}`} className={styles.observationItem}>
                  <h3>{observation.agentName}</h3>
                  <div>调用时间：{observation.createdAt ? new Date(observation.createdAt).toLocaleString() : "-"}</div>
                  <div>Token：输入 {observation.promptTokens ?? "-"} / 输出 {observation.completionTokens ?? "-"} / 总计 {observation.totalTokens ?? "-"}</div>
                  <div>下一步：{observation.nextDecision ?? "-"}</div>
                  {observation.input ? <details><summary>输入</summary><pre>{observation.input}</pre></details> : null}
                  {observation.output ? <details><summary>输出</summary><pre>{observation.output}</pre></details> : null}
                </article>
              ))}
            </div>
          </section>
        </div>
      ) : null}
    </main>
  );
}
