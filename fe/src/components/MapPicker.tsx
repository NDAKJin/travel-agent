import { KeyboardEvent, useEffect, useRef, useState } from "react";
import AMapLoader from "@amap/amap-jsapi-loader";
import { api } from "../services/api";
import styles from "./MapPicker.module.css";

type MapPickerProps = {
  accessToken: string;
  longitude: string;
  latitude: string;
  onChange: (longitude: string, latitude: string) => void;
};

type AMapPoi = {
  id: string;
  name: string;
  address?: string;
  location?: { lng?: number; lat?: number; getLng?: () => number; getLat?: () => number };
};

const DEFAULT_CENTER = [116.397428, 39.90923];
const AMAP_KEY = import.meta.env.VITE_AMAP_KEY;
const AMAP_SECURITY_CODE = import.meta.env.VITE_AMAP_SECURITY_JS_CODE;

const getPoint = (location: NonNullable<AMapPoi["location"]>) => {
  const lng = typeof location.getLng === "function" ? location.getLng() : location.lng;
  const lat = typeof location.getLat === "function" ? location.getLat() : location.lat;
  return Number.isFinite(lng) && Number.isFinite(lat) ? { lng: Number(lng), lat: Number(lat) } : null;
};

declare global {
  interface Window {
    _AMapSecurityConfig?: { securityJsCode: string };
  }
}

export default function MapPicker({ accessToken, longitude, latitude, onChange }: MapPickerProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const amapRef = useRef<any>(null);
  const mapRef = useRef<any>(null);
  const markerRef = useRef<any>(null);
  const onChangeRef = useRef(onChange);
  const [keyword, setKeyword] = useState("");
  const [results, setResults] = useState<AMapPoi[]>([]);
  const [mapError, setMapError] = useState("");
  const [searching, setSearching] = useState(false);
  onChangeRef.current = onChange;

  useEffect(() => {
    if (!containerRef.current || mapRef.current) return;
    if (!AMAP_KEY) {
      setMapError("未配置高德地图 Key，请设置 VITE_AMAP_KEY。");
      return;
    }
    if (AMAP_SECURITY_CODE) {
      window._AMapSecurityConfig = { securityJsCode: AMAP_SECURITY_CODE };
    }

    let disposed = false;
    AMapLoader.load({ key: AMAP_KEY, version: "2.0" })
      .then((AMap: any) => {
        if (disposed || !containerRef.current) return;
        amapRef.current = AMap;
        const initialLatitude = Number(latitude);
        const initialLongitude = Number(longitude);
        const hasInitialPoint = Number.isFinite(initialLatitude) && Number.isFinite(initialLongitude);
        const map = new AMap.Map(containerRef.current, {
          center: hasInitialPoint ? [initialLongitude, initialLatitude] : DEFAULT_CENTER,
          zoom: hasInitialPoint ? 15 : 11
        });
        mapRef.current = map;

        const updatePoint = (point: { lng: number; lat: number }) => {
          markerRef.current?.setMap(null);
          markerRef.current = new AMap.Marker({ position: [point.lng, point.lat], map });
          map.setCenter([point.lng, point.lat]);
          onChangeRef.current(point.lng.toFixed(6), point.lat.toFixed(6));
          setResults([]);
        };

        map.on("click", (event: any) => updatePoint(event.lnglat));
        if (hasInitialPoint) {
          markerRef.current = new AMap.Marker({ position: [initialLongitude, initialLatitude], map });
        }
      })
      .catch((error: unknown) => {
        if (!disposed) {
          console.error("[amap] map initialization failed", error);
          setMapError("高德地图加载失败，请检查 Key、安全密钥和网络配置。");
        }
      });

    return () => {
      disposed = true;
      markerRef.current?.setMap(null);
      mapRef.current?.destroy();
      markerRef.current = null;
      mapRef.current = null;
      amapRef.current = null;
    };
  }, []);

  useEffect(() => {
    const map = mapRef.current;
    const nextLatitude = Number(latitude);
    const nextLongitude = Number(longitude);
    if (!map || !Number.isFinite(nextLatitude) || !Number.isFinite(nextLongitude)) return;

    const point = [nextLongitude, nextLatitude];
    if (!markerRef.current && amapRef.current) {
      markerRef.current = new amapRef.current.Marker({ position: point, map });
    } else {
      markerRef.current.setPosition(point);
    }
    map.setCenter(point);
  }, [latitude, longitude]);

  const searchPlaces = () => {
    if (!keyword.trim()) {
      return;
    }
    setSearching(true);
    setMapError("");
    api.searchMapPlaces(accessToken, keyword.trim())
      .then(places => {
        const nextResults = places.map(place => ({
          id: place.id,
          name: place.name,
          address: place.address,
          location: { lng: place.longitude, lat: place.latitude }
        }));
        setResults(nextResults);
        if (nextResults.length === 0) setMapError("没有找到相关地点，请换一个关键词试试");
      })
      .catch(error => {
        setResults([]);
        setMapError(error instanceof Error ? error.message : "地图搜索失败，请稍后重试");
      })
      .finally(() => setSearching(false));
  };

  const selectPlace = (poi: AMapPoi) => {
    const point = poi.location ? getPoint(poi.location) : null;
    if (!point || !mapRef.current) return;
    markerRef.current?.setMap(null);
    if (!amapRef.current) return;
    markerRef.current = new amapRef.current.Marker({ position: [point.lng, point.lat], map: mapRef.current });
    mapRef.current.setZoomAndCenter(15, [point.lng, point.lat]);
    onChange(point.lng.toFixed(6), point.lat.toFixed(6));
    setKeyword(poi.name);
    setResults([]);
  };

  const clearSearch = () => {
    setKeyword("");
    setResults([]);
    setMapError("");
  };

  return (
    <div className={styles.wrapper}>
      <div
        className={styles.searchBar}
        role="search"
        onKeyDown={(event: KeyboardEvent<HTMLDivElement>) => {
          if (event.key === "Enter") {
            event.preventDefault();
            searchPlaces();
          }
        }}
      >
        <input value={keyword} onChange={event => setKeyword(event.target.value)} placeholder="搜索景区、地址或地标" aria-label="搜索景区、地址或地标" />
        <button type="button" className={styles.searchButton} onClick={searchPlaces} disabled={searching || !keyword.trim()}>{searching ? "搜索中..." : "搜索地点"}</button>
      </div>
      {results.length > 0 ? (
        <div className={styles.results}>
          {results.map(poi => (
            <button type="button" className={styles.resultItem} key={poi.id} onClick={() => selectPlace(poi)}>
              <strong>{poi.name}</strong>
              <span>{poi.address || "高德地图地点"}</span>
            </button>
          ))}
        </div>
      ) : null}
      <div className={styles.map} ref={containerRef} />
      {mapError ? <div className={styles.error}>{mapError}</div> : <div className={styles.hint}>搜索地点后选择结果，再点击地图确认位置；经纬度会自动填入。</div>}
    </div>
  );
}
