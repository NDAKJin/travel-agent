import { useEffect, useRef, useState } from "react";
import AMapLoader from "@amap/amap-jsapi-loader";
import type { AdminScenicSpot } from "../types";
import styles from "./ScenicSpotsMap.module.css";

const DEFAULT_CENTER = [116.397428, 39.90923];
const AMAP_KEY = import.meta.env.VITE_AMAP_KEY;
const AMAP_SECURITY_CODE = import.meta.env.VITE_AMAP_SECURITY_JS_CODE;

declare global {
  interface Window {
    _AMapSecurityConfig?: { securityJsCode: string };
  }
}

type ScenicSpotsMapProps = {
  spots: AdminScenicSpot[];
  onSpotClick?: (spot: AdminScenicSpot) => void;
};

export default function ScenicSpotsMap({ spots, onSpotClick }: ScenicSpotsMapProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const amapRef = useRef<any>(null);
  const mapRef = useRef<any>(null);
  const markersRef = useRef<any[]>([]);
  const onSpotClickRef = useRef(onSpotClick);
  const [mapError, setMapError] = useState("");
  const [mapReady, setMapReady] = useState(false);

  useEffect(() => {
    onSpotClickRef.current = onSpotClick;
  }, [onSpotClick]);

  useEffect(() => {
    if (!containerRef.current || mapRef.current) return;
    if (!AMAP_KEY) {
      setMapError("未配置高德地图 Key");
      return;
    }
    if (AMAP_SECURITY_CODE) window._AMapSecurityConfig = { securityJsCode: AMAP_SECURITY_CODE };

    let disposed = false;
    AMapLoader.load({ key: AMAP_KEY, version: "2.0" })
      .then((AMap: any) => {
        if (disposed || !containerRef.current) return;
        amapRef.current = AMap;
        mapRef.current = new AMap.Map(containerRef.current, {
          center: DEFAULT_CENTER,
          zoom: 11,
          resizeEnable: true
        });
        setMapReady(true);
      })
      .catch(error => {
        console.error("[amap] scenic overview initialization failed", error);
        if (!disposed) setMapError("高德地图加载失败");
      });

    return () => {
      disposed = true;
      markersRef.current.forEach(marker => marker.setMap(null));
      markersRef.current = [];
      mapRef.current?.destroy();
      mapRef.current = null;
      amapRef.current = null;
      setMapReady(false);
    };
  }, []);

  useEffect(() => {
    const map = mapRef.current;
    const AMap = amapRef.current;
    if (!map || !AMap) return;

    markersRef.current.forEach(marker => marker.setMap(null));
    markersRef.current = [];
    const validSpots = spots.filter(spot => Number.isFinite(spot.longitude) && Number.isFinite(spot.latitude));
    validSpots.forEach(spot => {
      const marker = new AMap.Marker({
        position: [spot.longitude, spot.latitude],
        title: spot.name,
        label: {
          content: `<span class="${styles.markerLabel}">${spot.name}</span>`,
          direction: "top",
          offset: new AMap.Pixel(0, -8)
        },
        map
      });
      marker.on("click", () => onSpotClickRef.current?.(spot));
      markersRef.current.push(marker);
    });

    if (validSpots.length === 1) {
      map.setZoomAndCenter(14, [validSpots[0].longitude, validSpots[0].latitude]);
    } else if (validSpots.length > 1) {
      map.setFitView(markersRef.current, false, [48, 48, 48, 48]);
    } else {
      map.setZoomAndCenter(11, DEFAULT_CENTER);
    }
  }, [spots, mapReady]);

  return (
    <div className={styles.wrapper}>
      <div className={styles.map} ref={containerRef} />
      {mapError ? <div className={styles.error}>{mapError}</div> : null}
      {!mapError && spots.length === 0 ? <div className={styles.empty}>暂无景区点位</div> : null}
    </div>
  );
}
