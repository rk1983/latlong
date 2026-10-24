import json
import os
import random
import re
import requests
from shapely.geometry import shape, Point, Polygon, MultiPolygon
from shapely.prepared import prep

DATASET_URL = "https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_50m_admin_0_countries.geojson"
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(SCRIPT_DIR)

OUTPUT_JSON_PATH = os.path.join(
    PROJECT_ROOT,
    "shared", "src", "commonMain", "composeResources", "files", "countries_game_data.json"
)
DRAWABLE_DIR = os.path.join(
    PROJECT_ROOT,
    "shared", "src", "commonMain", "composeResources", "drawable"
)
FLAG_RESOURCES_PATH = os.path.join(
    PROJECT_ROOT,
    "shared", "src", "commonMain", "kotlin", "no", "madival", "latlong", "ui", "FlagResources.kt"
)
CACHE_GEOJSON = os.path.join(SCRIPT_DIR, "ne_50m_admin_0_countries.geojson")

def download_geojson():
    if os.path.exists(CACHE_GEOJSON) and os.path.getsize(CACHE_GEOJSON) > 100000:
        print(f"Using cached GeoJSON: {CACHE_GEOJSON}")
        with open(CACHE_GEOJSON, "r", encoding="utf-8") as f:
            return json.load(f)
    print(f"Downloading Natural Earth 1:50m dataset from {DATASET_URL}...")
    resp = requests.get(DATASET_URL, timeout=60)
    resp.raise_for_status()
    data = resp.json()
    with open(CACHE_GEOJSON, "w", encoding="utf-8") as f:
        json.dump(data, f)
    print(f"Saved cache to {CACHE_GEOJSON}")
    return data

def get_country_code(props):
    code = props.get("ISO_A3", "")
    if not code or code == "-99":
        code = props.get("ADM0_A3", "")
    if not code or code == "-99":
        code = props.get("GU_A3", "")
    if not code or code == "-99":
        code = props.get("SU_A3", "")
    if not code or code == "-99":
        code = props.get("SOV_A3", "")
    return str(code).strip()

def get_iso2_code(props):
    code = props.get("ISO_A2_EH") or props.get("ISO_A2") or props.get("WB_A2") or ""
    code = str(code).strip().lower()
    if code == "-99" or not code.isalpha() or len(code) != 2:
        return ""
    return code

def compute_ring_bbox(coords):
    lons = [p[0] for p in coords]
    lats = [p[1] for p in coords]
    return [round(min(lons), 2), round(min(lats), 2), round(max(lons), 2), round(max(lats), 2)]

def sample_interior_points(geom, rings_info, count=15):
    prepared_geom = prep(geom)
    min_x, min_y, max_x, max_y = geom.bounds
    points = []
    
    try:
        rep_pt = geom.representative_point()
        ring_bbox = find_containing_ring_bbox(rep_pt, rings_info)
        points.append({
            "lon": round(float(rep_pt.x), 2),
            "lat": round(float(rep_pt.y), 2),
            "ringBbox": ring_bbox
        })
    except Exception:
        pass
        
    attempts = 0
    max_attempts = 2500
    while len(points) < count and attempts < max_attempts:
        attempts += 1
        rx = random.uniform(min_x, max_x)
        ry = random.uniform(min_y, max_y)
        pt = Point(rx, ry)
        if prepared_geom.contains(pt):
            r_bbox = find_containing_ring_bbox(pt, rings_info)
            points.append({
                "lon": round(rx, 2),
                "lat": round(ry, 2),
                "ringBbox": r_bbox
            })
            
    if len(points) < count and points:
        base_x = points[0]["lon"]
        base_y = points[0]["lat"]
        base_bbox = points[0]["ringBbox"]
        while len(points) < count:
            offset_x = (random.random() - 0.5) * 0.05
            offset_y = (random.random() - 0.5) * 0.05
            cand = Point(base_x + offset_x, base_y + offset_y)
            if prepared_geom.contains(cand):
                points.append({
                    "lon": round(float(cand.x), 2),
                    "lat": round(float(cand.y), 2),
                    "ringBbox": base_bbox
                })
            else:
                points.append({
                    "lon": base_x,
                    "lat": base_y,
                    "ringBbox": base_bbox
                })
                
    return points[:count]

def find_containing_ring_bbox(pt, rings_info):
    for ring in rings_info:
        poly = ring["shapely_poly"]
        if poly.contains(pt) or poly.touches(pt):
            return ring["bbox"]
    return rings_info[0]["bbox"] if rings_info else [-180.0, -90.0, 180.0, 90.0]

def download_flag(iso2):
    """
    Downloads flag image from flagcdn.com (w320 PNG) and saves as flag_{iso2}.png
    Returns True if flag exists and is saved, False otherwise.
    """
    os.makedirs(DRAWABLE_DIR, exist_ok=True)
    target_path = os.path.join(DRAWABLE_DIR, f"flag_{iso2}.png")
    if os.path.exists(target_path) and os.path.getsize(target_path) > 100:
        return True
    
    url = f"https://flagcdn.com/w320/{iso2}.png"
    try:
        r = requests.get(url, timeout=10)
        if r.status_code == 200 and len(r.content) > 50:
            with open(target_path, "wb") as f:
                f.write(r.content)
            return True
        else:
            print(f"Warning: flag for {iso2} returned {r.status_code}")
            return False
    except Exception as e:
        print(f"Error downloading flag for {iso2}: {e}")
        return False

def generate_flag_resources_kt(valid_flags):
    """
    Generates a type-safe Kotlin registry for all downloaded flags
    using Compose Multiplatform generated accessors.
    """
    sorted_flags = sorted(list(valid_flags))
    lines = [
        "package no.madival.latlong.ui",
        "",
        "import latlong.shared.generated.resources.Res",
        "import latlong.shared.generated.resources.*",
        "import org.jetbrains.compose.resources.DrawableResource",
        "",
        "object FlagResources {",
        "    private val flags: Map<String, DrawableResource> by lazy {",
        "        mapOf("
    ]
    
    for f in sorted_flags:
        lines.append(f'            "{f}" to Res.drawable.flag_{f},')
        
    lines.extend([
        "        )",
        "    }",
        "",
        "    fun getFlag(iso2: String): DrawableResource? {",
        "        return flags[iso2.lowercase()]",
        "    }",
        "}",
        ""
    ])
    
    os.makedirs(os.path.dirname(FLAG_RESOURCES_PATH), exist_ok=True)
    with open(FLAG_RESOURCES_PATH, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))
    print(f"Generated FlagResources.kt with {len(sorted_flags)} flag mappings.")

def main():
    geojson = download_geojson()
    countries = []
    
    features = geojson.get("features", [])
    print(f"Total features in 1:50m GeoJSON: {len(features)}")
    
    seen_codes = set()
    downloaded_flags = set()
    
    for feat in features:
        props = feat.get("properties", {})
        geom_dict = feat.get("geometry")
        if not geom_dict:
            continue
            
        code = get_country_code(props)
        iso2 = get_iso2_code(props)
        name = props.get("NAME") or props.get("ADMIN") or props.get("NAME_LONG")
        continent = props.get("CONTINENT") or "Unknown"
        subregion = props.get("SUBREGION") or continent
        
        # Filter out Antarctica and unpopulated glaciers
        if code in ("ATA", "AQ") or name in ("Antarctica", "Siachen Glacier"):
            continue
        if not code or len(code) < 2:
            continue
            
        if code in seen_codes:
            code = f"{code}_{len(seen_codes)}"
        seen_codes.add(code)
            
        try:
            raw_geom = shape(geom_dict)
            if raw_geom.is_empty:
                continue
            geom = raw_geom.simplify(0.04, preserve_topology=True)
            if geom.is_empty:
                geom = raw_geom
        except Exception as e:
            print(f"Skipping {name} due to geometry error: {e}")
            continue
            
        rings_info = []
        if isinstance(geom, Polygon):
            ext_coords = [[round(c[0], 2), round(c[1], 2)] for c in geom.exterior.coords]
            if len(ext_coords) >= 3:
                r_bbox = compute_ring_bbox(ext_coords)
                rings_info.append({
                    "bbox": r_bbox,
                    "points": ext_coords,
                    "shapely_poly": Polygon(geom.exterior)
                })
        elif isinstance(geom, MultiPolygon):
            for poly in geom.geoms:
                ext_coords = [[round(c[0], 2), round(c[1], 2)] for c in poly.exterior.coords]
                if len(ext_coords) >= 3:
                    r_bbox = compute_ring_bbox(ext_coords)
                    rings_info.append({
                        "bbox": r_bbox,
                        "points": ext_coords,
                        "shapely_poly": Polygon(poly.exterior)
                    })
                    
        if not rings_info:
            continue
            
        sample_points = sample_interior_points(geom, rings_info, count=15)
        if not sample_points:
            continue
            
        all_lons = [pt[0] for r in rings_info for pt in r["points"]]
        all_lats = [pt[1] for r in rings_info for pt in r["points"]]
        global_bbox = [
            round(min(all_lons), 2),
            round(min(all_lats), 2),
            round(max(all_lons), 2),
            round(max(all_lats), 2)
        ]
        
        clean_rings = []
        for r in rings_info:
            clean_rings.append({
                "bbox": r["bbox"],
                "points": r["points"]
            })
            
        # Download flag if iso2 is available
        has_flag = False
        if iso2:
            if download_flag(iso2):
                downloaded_flags.add(iso2)
                has_flag = True
                
        countries.append({
            "code": code,
            "iso2": iso2 if has_flag else "",
            "name": name,
            "continent": continent,
            "subregion": subregion,
            "bbox": global_bbox,
            "rings": clean_rings,
            "samplePoints": sample_points
        })
        
    print(f"Processed {len(countries)} valid countries/territories.")
    print(f"Downloaded and verified {len(downloaded_flags)} flags.")
    
    os.makedirs(os.path.dirname(OUTPUT_JSON_PATH), exist_ok=True)
    out_dict = {"countries": countries}
    
    with open(OUTPUT_JSON_PATH, "w", encoding="utf-8") as f:
        json.dump(out_dict, f, separators=(',', ':'))
        
    file_size_kb = os.path.getsize(OUTPUT_JSON_PATH) / 1024
    print(f"Successfully generated: {OUTPUT_JSON_PATH}")
    print(f"File size: {file_size_kb:.1f} KB")
    
    generate_flag_resources_kt(downloaded_flags)

if __name__ == "__main__":
    main()
