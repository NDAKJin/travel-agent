import { type ChangeEvent, useEffect, useMemo, useState } from "react";
import styles from "../App.module.css";
import { api } from "../services/api";
import type { AuthSession, RagChunk, RagDocument, RagIngestionTask } from "../types";

export default function RagManagementPage({ session, onBack }: { session: AuthSession; onBack: () => void }) {
  const [documents, setDocuments] = useState<RagDocument[]>([]);
  const [chunks, setChunks] = useState<RagChunk[]>([]);
  const [tasks, setTasks] = useState<RagIngestionTask[]>([]);
  const [selectedDocument, setSelectedDocument] = useState<number | null>(null);
  const [tab, setTab] = useState<"documents" | "chunks">("documents");
  const [keyword, setKeyword] = useState("");
  const [enabledFilter, setEnabledFilter] = useState<"all" | "enabled" | "disabled">("all");
  const [files, setFiles] = useState<File[]>([]);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState("");
  const [statusVisible, setStatusVisible] = useState(true);
  const [statusExpanded, setStatusExpanded] = useState(true);

  const load = async () => {
    setLoading(true);
    try {
      const enabled = enabledFilter === "all" ? undefined : enabledFilter === "enabled";
      const [nextDocuments, nextChunks, nextTasks] = await Promise.all([
        api.listRagDocuments(session.token.accessToken, keyword),
        api.listRagChunks(session.token.accessToken, { documentId: selectedDocument ?? undefined, keyword, enabled }),
        api.listRagIngestionTasks(session.token.accessToken)
      ]);
      setDocuments(nextDocuments);
      setChunks(nextChunks);
      setTasks(nextTasks);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "RAG 数据加载失败");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { void load(); }, [selectedDocument, enabledFilter]);
  useEffect(() => {
    if (!tasks.some(task => task.status === "PENDING" || task.status === "RUNNING")) return;
    const timer = window.setInterval(() => void load(), 2000);
    return () => window.clearInterval(timer);
  }, [tasks]);

  const importFiles = async () => {
    if (!files.length) return;
    setLoading(true);
    setMessage("");
    try {
      const results = await api.importRagDocuments(session.token.accessToken, files);
      const failed: RagIngestionTask[] = [];
      setStatusVisible(true);
      setStatusExpanded(true);
      setMessage(failed.length ? `完成 ${results.length - failed.length} 个，失败 ${failed.length} 个` : `已导入 ${results.length} 个文件`);
      setMessage(`已提交 ${results.length} 个文件，后台正在解析和向量化，可在下方查看进度`);
      setFiles([]);
      await load();
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "导入失败");
    } finally {
      setLoading(false);
    }
  };

  const cancelTask = async (task: RagIngestionTask) => {
    try {
      await api.cancelRagIngestionTask(session.token.accessToken, task.id);
      setMessage(`已取消导入任务：${task.fileName}`);
      await load();
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "取消任务失败");
    }
  };

  const toggleDocument = async (document: RagDocument) => {
    setLoading(true);
    try {
      await api.toggleRagDocument(session.token.accessToken, document.id, !document.enabled);
      setMessage(`${document.enabled ? "已禁用" : "已启用"}文档及其 Chunk`);
      await load();
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "修改文档状态失败");
    } finally {
      setLoading(false);
    }
  };

  const toggleChunk = async (chunk: RagChunk) => {
    setLoading(true);
    try {
      await api.toggleRagChunk(session.token.accessToken, chunk.id, !chunk.enabled);
      setMessage(chunk.enabled ? "Chunk 已禁用" : "Chunk 已启用");
      await load();
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "修改 Chunk 状态失败");
    } finally {
      setLoading(false);
    }
  };

  const visibleChunks = useMemo(
    () => selectedDocument == null ? chunks : chunks.filter(item => item.documentId === selectedDocument),
    [chunks, selectedDocument]
  );

  return <main className={styles.appPage}>
    <aside className={styles.sidebar}>
      <div className={styles.logo}><span>✦</span><div>TRAVEL<br /><small>AGENT OPS</small></div></div>
      <div className={styles.sidebarLabel}>WORKSPACE</div>
      <button className={styles.navButton} type="button" onClick={onBack}>▦ <span>运营看板</span></button>
      <button className={styles.navActive} type="button">⌁ <span>旅行知识库</span></button>
    </aside>
    <section className={styles.mainPanel}><div className={styles.detailPage}>
    <header className={styles.detailHeader}><div><button type="button" className={styles.backButton} onClick={onBack}>← 返回看板</button><div className={styles.kicker}>RAG KNOWLEDGE BASE</div><h1>旅行知识库</h1><p className={styles.muted}>管理文章、分块和向量化结果</p></div></header>
    <section className={styles.ragToolbar}><label className={styles.uploadButton}>选择文件<input type="file" multiple onChange={(event: ChangeEvent<HTMLInputElement>) => setFiles(Array.from(event.target.files ?? []))} /></label><span className={styles.muted}>{files.length ? `已选择 ${files.length} 个文件` : "支持 PDF、Word、Markdown、TXT 等格式"}</span><button type="button" className={styles.primarySmallButton} disabled={!files.length || loading} onClick={() => void importFiles()}>{loading ? "处理中..." : "开始导入"}</button><button type="button" className={styles.refreshButton} onClick={() => void load()}>刷新</button></section>
    {message ? <div className={styles.successBanner}>{message}</div> : null}
    {tasks.length && statusVisible ? <section className={styles.statusTracker}><div className={styles.statusTrackerHeader}><div><strong>导入状态追踪</strong><small>{tasks.filter(task => task.status === "PENDING" || task.status === "RUNNING").length ? "任务处理中，页面会自动刷新" : "最近导入任务已完成"}</small></div><div className={styles.statusTrackerActions}><button type="button" className={styles.statusAction} onClick={() => setStatusExpanded(value => !value)}>{statusExpanded ? "收起" : "展开"}</button><button type="button" className={styles.statusClose} aria-label="关闭导入状态栏" onClick={() => setStatusVisible(false)}>×</button></div></div>{statusExpanded ? <div className={styles.statusTrackerList}>{tasks.slice(0, 8).map(task => <div className={styles.statusTrackerItem} key={task.id}><span className={`${styles.statusDot} ${task.status === "SUCCESS" ? styles.statusSuccess : task.status === "FAILED" ? styles.statusFailed : styles.statusPending}`} /><span className={styles.statusFile}>{task.fileName}</span><span className={styles.statusText}>{task.chunkCount} 个 Chunk · {task.writtenCount} 个已写入</span><span className={`${styles.statusText} ${task.status === "FAILED" ? styles.statusTextFailed : ""}`}>{task.status === "SUCCESS" ? "已完成" : task.status === "FAILED" ? "失败" : task.status === "RUNNING" ? "处理中" : task.status}</span>{task.error ? <span className={styles.statusError} title={task.error}>{task.error}</span> : null}</div>)}</div> : null}</section> : tasks.length ? <button type="button" className={styles.statusLauncher} onClick={() => { setStatusVisible(true); setStatusExpanded(true); }}>查看导入状态（{tasks.length}）</button> : null}
    {tasks.some(task => task.status === "PENDING" || task.status === "DISPATCHED" || task.status === "RUNNING") ? <div className={styles.statusTrackerActions}>{tasks.filter(task => task.status === "PENDING" || task.status === "DISPATCHED" || task.status === "RUNNING").map(task => <button key={task.id} type="button" className={styles.statusAction} onClick={() => void cancelTask(task)}>取消「{task.fileName}」</button>)}</div> : null}
    <section className={styles.ragTabs}><button className={tab === "documents" ? styles.tabActive : styles.tabButton} type="button" onClick={() => setTab("documents")}>按文章管理 <b>{documents.length}</b></button><button className={tab === "chunks" ? styles.tabActive : styles.tabButton} type="button" onClick={() => setTab("chunks")}>按 Chunk 管理 <b>{visibleChunks.length}</b></button><select value={enabledFilter} onChange={event => setEnabledFilter(event.target.value as typeof enabledFilter)}><option value="all">全部状态</option><option value="enabled">仅启用</option><option value="disabled">仅禁用</option></select><div className={styles.ragSearch}><input placeholder="搜索文件名、标题或关键词" value={keyword} onChange={event => setKeyword(event.target.value)} onKeyDown={event => { if (event.key === "Enter") void load(); }} /><button type="button" onClick={() => void load()}>搜索</button></div></section>
    {tab === "documents" ? <section className={styles.ragGrid}>{documents.map(document => <article className={styles.ragDocumentCard} key={document.id}><div className={styles.ragDocumentTop}><span className={styles.fileBadge}>DOC</span><span className={styles.countPill}>{document.chunkCount} chunks · {document.enabled ? "启用" : "禁用"}</span></div><h2>{document.title || document.fileName}</h2><p className={styles.muted}>{document.fileName} · {document.author || "未知作者"}</p><p>{document.summary || "暂无摘要"}</p><div className={styles.ragMeta}>{document.keywords || "暂无关键词"}</div><button type="button" className={styles.textButton} disabled={loading} onClick={() => void toggleDocument(document)}>{document.enabled ? "禁用文档" : "启用文档"}</button><button type="button" className={styles.textButton} onClick={() => { setSelectedDocument(document.id); setTab("chunks"); }}>查看分块 →</button></article>)}{!documents.length ? <div className={styles.empty}>暂无知识文章，请先导入文件</div> : null}</section> : <section className={styles.chunkList}>{visibleChunks.map(chunk => <article className={styles.chunkCard} key={chunk.id}><div className={styles.chunkHeader}><strong>{chunk.fileName} · Chunk {chunk.chunkIndex}</strong><span>{chunk.startOffset} - {chunk.endOffset} · {chunk.enabled ? "启用" : "禁用"}</span></div><p>{chunk.content}</p><small>{chunk.summary || "暂无摘要"} {chunk.keywords ? ` · ${chunk.keywords}` : ""}</small><button type="button" className={styles.textButton} disabled={loading} onClick={() => void toggleChunk(chunk)}>{chunk.enabled ? "禁用 Chunk" : "启用 Chunk"}</button></article>)}{!visibleChunks.length ? <div className={styles.empty}>暂无 Chunk 数据</div> : null}</section>}
    </div></section>
  </main>;
}
