import { FormEvent, useEffect, useState } from "react";
import styles from "../App.module.css";
import MapPicker from "./MapPicker";
import ScenicSpotsMap from "./ScenicSpotsMap";
import { api } from "../services/api";
import type { AdminScenicSpot, AdminServicePoint } from "../types";

type Props = { accessToken: string; authorized: <T>(action: (token: string) => Promise<T>) => Promise<T>; onToast: (message: string) => void; forceEditor?: boolean; onBack?: () => void; onCreatePage?: () => void };
type FormState = { name: string; category: string; description: string; address: string; longitude: string; latitude: string };
const empty: FormState = { name: "", category: "停车场", description: "", address: "", longitude: "", latitude: "" };

export default function ServicePointsPage({ accessToken, authorized, onToast, forceEditor = false, onBack, onCreatePage }: Props) {
  const [items, setItems] = useState<AdminServicePoint[]>([]);
  const [total, setTotal] = useState(0);
  const [form, setForm] = useState<FormState>(empty);
  const [editing, setEditing] = useState<string | null>(null);
  const [editorOpen, setEditorOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [categories, setCategories] = useState<string[]>(["停车场", "文旅服务点", "卫生间"]);
  const [categoryFilter, setCategoryFilter] = useState("全部类型");
  const [page, setPage] = useState(1);
  const pageSize = 6;

  const load = async () => {
    setLoading(true);
    try {
      const category = categoryFilter === "全部类型" ? undefined : categoryFilter;
      const [result, types] = await Promise.all([
        authorized(token => api.listServicePoints(token, { category, page, size: pageSize })),
        authorized(token => api.listServicePointCategories(token))
      ]);
      setItems(result.content);
      setTotal(result.total);
      if (types.length) setCategories(types);
    }
    catch (e) { setError(e instanceof Error ? e.message : "加载便民服务失败"); }
    finally { setLoading(false); }
  };
  useEffect(() => { void load(); }, [page, categoryFilter]);
  const update = (key: keyof FormState, value: string) => setForm(current => ({ ...current, [key]: value }));
  const chooseCategory = async (value: string) => {
    if (value !== "__create__") { update("category", value); return; }
    const name = window.prompt("请输入新类型名称");
    if (!name?.trim()) return;
    try { const created = await authorized(token => api.createServicePointCategory(token, name.trim())); setCategories(current => current.includes(created) ? current : [...current, created]); update("category", created); onToast("类型已创建"); }
    catch (e) { setError(e instanceof Error ? e.message : "创建类型失败"); }
  };
  const openCreate = () => { if (onCreatePage) { onCreatePage(); return; } setEditing(null); setForm(empty); setError(""); setEditorOpen(true); };
  const openEdit = (item: AdminServicePoint) => { setEditing(item.id); setCategories(current => current.includes(item.category) ? current : [...current, item.category]); setForm({ name: item.name, category: item.category, description: item.description, address: item.address, longitude: String(item.longitude), latitude: String(item.latitude) }); setError(""); setEditorOpen(true); };
  const closeEditor = () => { if (forceEditor) { onBack?.(); return; } setEditorOpen(false); setEditing(null); setError(""); };
  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const longitude = Number(form.longitude), latitude = Number(form.latitude);
    if (!form.name.trim() || !form.category.trim() || !form.description.trim() || !Number.isFinite(longitude) || !Number.isFinite(latitude)) { setError("请完整填写名称、类型、介绍和经纬度"); return; }
    setSaving(true); setError("");
    const payload = { name: form.name.trim(), category: form.category.trim(), description: form.description.trim(), address: form.address.trim(), longitude, latitude };
    try {
      const wasEditing = Boolean(editing);
      await authorized(token => editing ? api.updateServicePoint(token, editing, payload) : api.createServicePoint(token, payload));
      setPage(1);
      if (page === 1) await load();
      closeEditor(); onToast(wasEditing ? "便民服务已更新" : "便民服务已创建");
    } catch (e) { setError(e instanceof Error ? e.message : "保存失败"); }
    finally { setSaving(false); }
  };
  const remove = async (item: AdminServicePoint) => {
    if (!window.confirm(`确认删除“${item.name}”？`)) return;
    try { await authorized(token => api.deleteServicePoint(token, item.id)); setPage(1); setTotal(current => Math.max(0, current - 1)); if (page === 1) await load(); onToast("便民服务已删除"); }
    catch (e) { onToast(e instanceof Error ? e.message : "删除失败"); }
  };

  if (editorOpen || forceEditor) return <section className={styles.serviceEditorShell}>
    <div className={styles.serviceEditorTopBar}><button type="button" className={styles.backButton} onClick={closeEditor}>返回便民服务</button><div><div className={styles.eyebrow}>便民服务</div><h2 className={styles.sectionTitle}>{editing ? "编辑便民服务" : "新建便民服务"}</h2></div></div>
    <form className={styles.serviceEditorCard} onSubmit={submit}><div className={styles.scenicEditorFields}><label className={styles.field}><span>名称</span><input value={form.name} onChange={e => update("name", e.target.value)} placeholder="例如：东门停车场" /></label><label className={styles.field}><span>类型</span><select value={categories.includes(form.category) ? form.category : ""} onChange={e => chooseCategory(e.target.value)}><option value="" disabled>请选择类型</option>{categories.map(category => <option key={category} value={category}>{category}</option>)}<option value="__create__">＋ 新建类型</option></select></label><label className={styles.field}><span>地址</span><input value={form.address} onChange={e => update("address", e.target.value)} placeholder="详细地址（可选）" /></label><label className={styles.field}><span>介绍</span><textarea value={form.description} onChange={e => update("description", e.target.value)} rows={6} placeholder="开放时间、设施说明、联系电话等" /></label></div><div className={styles.editorMapPanel}><div className={styles.cardHeader}>地图位置</div><MapPicker accessToken={accessToken} longitude={form.longitude} latitude={form.latitude} onChange={(longitude, latitude) => setForm(current => ({ ...current, longitude, latitude }))} /><div className={styles.formRow}><label className={styles.field}><span>经度</span><input type="number" step="any" value={form.longitude} onChange={e => update("longitude", e.target.value)} /></label><label className={styles.field}><span>纬度</span><input type="number" step="any" value={form.latitude} onChange={e => update("latitude", e.target.value)} /></label></div></div>{error ? <div className={styles.errorText}>{error}</div> : null}<div className={styles.editorFooter}><span className={styles.helperText}>类型可通过下拉框最后一项随时扩展。</span><button className={styles.primaryButton} disabled={saving}>{saving ? "保存中..." : editing ? "保存修改" : "创建服务点"}</button></div></form>
  </section>;

  const totalPages = Math.max(1, Math.ceil(total / pageSize));
  const safePage = Math.min(page, totalPages);
  const visibleItems = items;
  const mapSpots: AdminScenicSpot[] = items.map(item => ({ ...item }));
  return <div className={styles.serviceOverview}><div className={styles.serviceOverviewHeader}><div><div className={styles.eyebrow}>便民服务</div><h2 className={styles.sectionTitle}>服务点地图与列表</h2><p className={styles.serviceOverviewLead}>集中查看停车场、文旅服务点、卫生间等便民位置。</p></div><button type="button" className={styles.primaryButton} onClick={openCreate}>新建便民服务</button></div><div className={styles.serviceOverviewGrid}><div className={styles.cardPanel}><div className={styles.serviceListToolbar}><div className={styles.cardHeader}>全部服务点</div><select value={categoryFilter} onChange={event => { setCategoryFilter(event.target.value); setPage(1); }}><option>全部类型</option>{categories.map(category => <option key={category}>{category}</option>)}</select></div><div className={styles.listBlock}>{visibleItems.map(item => <div className={`${styles.scenicCompactItem} ${styles.servicePointItem}`} key={item.id}><div className={styles.listTitle}>{item.name}</div><div className={styles.listMeta}>{item.category} · {item.address || `${item.longitude}, ${item.latitude}`}</div><div className={styles.listPreview}>{item.description}</div><div className={styles.servicePointActions}><button type="button" className={styles.secondaryButton} onClick={() => openEdit(item)}>编辑</button><button type="button" className={styles.secondaryButton} onClick={() => void remove(item)}>删除</button></div></div>)}</div>{loading ? <div className={styles.helperText}>正在加载...</div> : null}{!loading && items.length === 0 ? <div className={styles.helperText}>暂无符合条件的便民服务点。</div> : null}<div className={styles.paginationBar}><span className={styles.paginationMeta}>第 {safePage} / {totalPages} 页，共 {total} 个</span><div className={styles.paginationButtons}><button type="button" className={styles.secondaryButton} disabled={safePage <= 1} onClick={() => setPage(value => Math.max(1, value - 1))}>上一页</button><button type="button" className={styles.secondaryButton} disabled={safePage >= totalPages} onClick={() => setPage(value => Math.min(totalPages, value + 1))}>下一页</button></div></div></div><div className={styles.cardPanel}><div className={styles.cardHeader}>服务点分布{categoryFilter !== "全部类型" ? ` · ${categoryFilter}` : ""}</div><ScenicSpotsMap spots={mapSpots} /></div></div></div>;
}
