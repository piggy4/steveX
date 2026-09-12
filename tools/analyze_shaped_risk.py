#!/usr/bin/env python3
"""v2.39 非满形状风险全量扫描：按两个"已被证明会致命"的量给所有方块模型排序。

背景：睡莲失明的根因不是深度、不是掩码，而是一个**从没被系统算过**的几何量——
"可见面到本格出口的距离"Δ。设计早期用直觉估计它是 1/16（0.0625），实测是 1/64（0.015625），
并因此让 ε=0.05 把整族贴花推出了本格（§4.2.1 决策 R）。本工具把该量对**全部**方块模型算出来，
免得再靠直觉。

两个风险轴（与文档口径一一对应）：

  【A 观察侧 / 落格 ε】Δ_min = 各可见面到自己所在格的**出口平面**的距离（格为单位）。
      像素被推进 ε 后离开本格 ⟺ ε > (t_exit − t_hit)，其中 t 为**射线参数**（ε 就是射线参数）。
      单轴近似下 ⟺ ε·|d_a| > Δ_a，故**失明 ⟺ |d_a| > ρ = Δ_a/ε**（d_a = 视线方向在面法线轴上的分量）。
        ρ = Δ_min/ε ≥ 1  ⇒ 任意角度都不失明（安全）
        ρ < 1            ⇒ 失明锥半角 = acos(ρ)：视线与法线轴夹角小于它即失明
                            （越"正对"该轴越危险，掠射反而安全）
        水平贴面（睡莲/红石粉/落叶）的特例：|d_y| = sin(俯角)，故 失明 ⟺ 俯角 > asin(ρ)
        —— 旧 ε=0.05 下 asin(1/64 ÷ 0.05) = 18.2°，与文档实测一致；asin(ρ) 与 acos(ρ) 互补，
        列出的"失明半角"取 acos(ρ)，读俯角时用 90° 减它。
      注意 Δ = 0（面正好贴在格界平面上）**不是**风险：那正是 nudge 要处理的"硬币翻转"群体，
      由 ε 的**下界**（ε > 0.5·1.192e-6·z²）保证。风险区间是 **0 < Δ < ε**。

  【B 删除侧 / δ 值域】h_max = 格内顶点最大 y（格为单位）⇒ δ_cell = clamp(min(0.05, 0.5·h_max), 0, …)。
      δ_cell 越小越容易满足可达性（好事），但**噪声下界** c·1.192e-6·z² 会追平它：
        z_close = sqrt(δ_cell / (c·1.192e-6))   ⇒ 超过该距离即"不可判"（欠删，§7 决策 Q）
      h_max 越小，窗口闭合越近。

用法：
  python analyze_shaped_risk.py                      # 全量，按 A 轴风险排序
  python analyze_shaped_risk.py --axis B             # 按 B 轴（δ/闭塞半径）排序
  python analyze_shaped_risk.py --grep slab,fence,carpet
  python analyze_shaped_risk.py --only-present       # 只列用户快照里实际出现过的方块
  python analyze_shaped_risk.py --snapshot <dir>     # 指定 terrain.nbt / memory_cells.bin 所在目录
"""

import argparse
import json
import math
import re
import struct
import sys
from pathlib import Path

# ---------------------------------------------------------------- 常量（须与 Java 侧一致）

EPS_LANDING = 1.0 / 128.0        # Unprojector.LANDING_EPSILON
QUANTUM_PER_Z2 = 1.19209e-6      # DeletionJudge.QUANTUM_PER_Z2（Δz(1ULP) = 该值 × z²）
DELTA_NOISE_C = 1.0              # DeletionJudge.DELTA_NOISE_C（待 §10 第 17 条标定）
DELTA_GLOBAL = 0.05              # DeletionJudge.DELTA_GLOBAL
KAPPA = 0.5                      # DeletionJudge.KAPPA

# 六个面：(轴, 该轴上的坐标取值来源, 法线方向)。Δ 的推导见 docstring。
FACES = (
    ("up",    "y", "to",   +1),   # 面在 y=to/16，法线 +y ⇒ 射线 d_y<0 ⇒ 出口 = 格底 ⇒ Δ = to/16
    ("down",  "y", "from", -1),   # 面在 y=from/16，法线 -y ⇒ 出口 = 格顶 ⇒ Δ = 1 - from/16
    ("east",  "x", "to",   +1),   # 同理
    ("west",  "x", "from", -1),
    ("south", "z", "to",   +1),
    ("north", "z", "from", -1),
)


def _axis_index(axis):
    return {"x": 0, "y": 1, "z": 2}[axis]


def _rotate(p, origin, axis, angle_deg):
    """元素 rotation：绕 origin 沿 axis 旋转（坐标均为 1/16 单位）。"""
    a = math.radians(angle_deg)
    c, s = math.cos(a), math.sin(a)
    i = _axis_index(axis)
    v = [p[0] - origin[0], p[1] - origin[1], p[2] - origin[2]]
    # MC 的元素旋转为绕轴左手/右手不定，取绝对值后对 Δ_min 无影响（只关心包围盒）
    j, k = (i + 1) % 3, (i + 2) % 3
    out = v[:]
    out[j] = v[j] * c - v[k] * s
    out[k] = v[j] * s + v[k] * c
    return [out[0] + origin[0], out[1] + origin[1], out[2] + origin[2]]


def element_box(el, rescale):
    """元素 → (lo, hi) 六元组（1/16 单位），含 rotation 后的包围盒。"""
    frm, to = el.get("from"), el.get("to")
    if frm is None or to is None:
        return None
    if rescale:
        # 模型级 rescale：[-16,32] 映射到 [0,16]
        frm = [(v + 16.0) / 3.0 for v in frm]
        to = [(v + 16.0) / 3.0 for v in to]
    lo = [min(frm[i], to[i]) for i in range(3)]
    hi = [max(frm[i], to[i]) for i in range(3)]
    rot = el.get("rotation")
    if rot:
        origin = rot.get("origin", [8, 8, 8])
        axis = rot.get("axis", "y")
        angle = rot.get("angle", 0.0)
        corners = []
        for m in range(8):
            p = [hi[i] if (m >> i) & 1 else lo[i] for i in range(3)]
            corners.append(_rotate(p, origin, axis, angle))
        lo = [min(c[i] for c in corners) for i in range(3)]
        hi = [max(c[i] for c in corners) for i in range(3)]
    return lo, hi


class ModelLib:
    """模型库：解析 parent 链、合并 elements（与 MC 的继承规则同构）。"""

    def __init__(self, root):
        self.root = root
        self.cache = {}

    def _load(self, path):
        try:
            with open(path, "r", encoding="utf-8") as fh:
                return json.load(fh)
        except Exception:
            return None

    def resolve(self, name):
        """name 为 'minecraft:block/xxx' 或 'xxx' → {elements, rescale}；解析失败返回 None。"""
        if name in self.cache:
            return self.cache[name]
        self.cache[name] = None  # 防环
        bare = name.split(":")[-1]
        rel = bare[len("block/"):] if bare.startswith("block/") else bare
        path = self.root / f"{rel}.json"
        if not path.exists():
            return None
        doc = self._load(path)
        if doc is None:
            return None
        rescale = bool(doc.get("rescale", False))
        elements = doc.get("elements")
        if elements is None and doc.get("parent"):
            parent = self.resolve(doc["parent"])
            if parent is None:
                return None
            # 子模型可自带 rescale；elements 继承自 parent
            merged = dict(parent)
            if rescale:
                merged = dict(merged, rescale=True)
            self.cache[name] = merged
            return merged
        if elements is None:
            return None
        out = {"elements": elements, "rescale": rescale}
        self.cache[name] = out
        return out

    def all_names(self):
        names = []
        for p in sorted(self.root.glob("*.json")):
            if p.name.endswith(".json") and not p.name.endswith(".mcmeta"):
                names.append(f"block/{p.stem}")
        return names


def _face_occluded(center, normal, boxes, self_idx, cell_lo, cell_hi):
    """从面心沿 −normal 向外走，在**出格之前**是否先撞到另一个元素。

    撞到 ⇒ 该面是本模型的内部面，外部射线射不到它（例：powder_snow 的三片薄壳，
    其"内表面"根本不是可见面）⇒ 不计入风险。
    """
    d = [-normal[0], -normal[1], -normal[2]]
    best_exit = float("inf")
    for i in range(3):
        if d[i] > 1e-12:
            best_exit = min(best_exit, (cell_hi[i] - center[i]) / d[i])
        elif d[i] < -1e-12:
            best_exit = min(best_exit, (cell_lo[i] - center[i]) / d[i])
    for j, (lo, hi) in enumerate(boxes):
        if j == self_idx:
            continue
        tmin, tmax = 0.0, best_exit
        hit = True
        for i in range(3):
            if abs(d[i]) < 1e-12:
                if center[i] < lo[i] or center[i] > hi[i]:
                    hit = False
                    break
                continue
            t1 = (lo[i] - center[i]) / d[i]
            t2 = (hi[i] - center[i]) / d[i]
            if t1 > t2:
                t1, t2 = t2, t1
            tmin = max(tmin, t1)
            tmax = min(tmax, t2)
            if tmin > tmax:
                hit = False
                break
        if hit and tmin > 1e-3 and tmin < best_exit:
            return True
    return False


def analyze(lib, name):
    """→ dict(ok, h_max, delta_cell, z_close, dmin, rho, crit_deg, n_el, note)"""
    res = lib.resolve(name)
    if res is None:
        return {"ok": False, "note": "无 elements（纯 parent 空模型 / 解析失败）"}
    rescale = res["rescale"]
    h_max = 0.0
    n_el = 0
    boxes = []
    for el in res["elements"]:
        box = element_box(el, rescale)
        if box is None:
            continue
        n_el += 1
        boxes.append(box)
        h_max = max(h_max, box[1][1] / 16.0)
    if n_el == 0:
        return {"ok": False, "note": "elements 为空"}

    cell_lo, cell_hi = [0.0, 0.0, 0.0], [16.0, 16.0, 16.0]
    dmin, dmin_face, dmin_cull, n_cand = None, None, 0, 0
    for idx, el in enumerate(res["elements"]):
        box = element_box(el, rescale)
        if box is None:
            continue
        lo, hi = box
        faces = el.get("faces") or {k: {} for k, _, _, _ in FACES}
        for fname, axis, end, nsign in FACES:
            fdef = faces.get(fname)
            if fdef is None:
                continue
            i = _axis_index(axis)
            coord = hi[i] if end == "to" else lo[i]
            # 出口方向与法线反号：Δ = 从面到该方向上格出口的距离
            delta = (coord / 16.0) if nsign > 0 else (1.0 - coord / 16.0)
            delta = max(0.0, min(1.0, delta))
            if delta >= EPS_LANDING:
                continue  # 安全带，不必再判可达性
            # 过滤器 ①：声明了 cullface 的面 —— 邻格不透明时根本不渲染；渲染了就说明邻格是
            # 空气/透明 ⇒ §5.1 的 air 近侧回退能把 W 还原 ⇒ 不可能失明。
            if fdef.get("cullface"):
                dmin_cull += 1
                continue
            # 过滤器 ②：被同模型其他元素遮挡的内部面，外部射线射不到。
            normal = [0.0, 0.0, 0.0]
            normal[i] = nsign
            center = [(lo[k] + hi[k]) / 2.0 for k in range(3)]
            center[i] = coord
            if _face_occluded(center, normal, boxes, idx, cell_lo, cell_hi):
                continue
            n_cand += 1
            if dmin is None or delta < dmin:
                dmin, dmin_face = delta, fname
    delta_cell = max(0.0, min(DELTA_GLOBAL, KAPPA * h_max))
    z_close = math.sqrt(delta_cell / (DELTA_NOISE_C * QUANTUM_PER_Z2)) if delta_cell > 0 else 0.0
    rho = (dmin / EPS_LANDING) if dmin is not None else float("inf")
    # 失明锥半角 = acos(ρ)：视线与法线轴夹角小于它即失明（|d_a| > ρ）。
    # 对水平贴面等价于「俯角 > asin(ρ)」= 90° − 本值。
    crit = None if rho >= 1.0 else math.degrees(math.acos(rho))
    # 分带（关键：Δ=0 与 0<Δ<ε 是**相反**的两侧，不能混排）
    if dmin is None:
        zone = "safe"       # 全部候选面都 ≥ ε（或将 cullface 的面已排除）⇒ 任意角度安全
    elif dmin == 0.0:
        zone = "BOUNDARY"   # 面正落在格界平面上：正是 nudge 要处理的群体，安全（但依赖 ε 下界）
    elif rho < 1.0:
        zone = "DANGER"     # 0 < Δ < ε：视线进入 acos(ρ) 失明锥就被推出本格 —— 睡莲那一类
    else:
        zone = "safe"
    return {
        "ok": True, "h_max": h_max, "delta_cell": delta_cell, "z_close": z_close,
        "dmin": dmin, "dmin_face": dmin_face, "rho": rho, "crit_deg": crit,
        "n_el": n_el, "rescale": rescale, "zone": zone,
        "n_cand": n_cand, "n_cull": dmin_cull,
    }


# ------------------------------------------------------- 用户快照：实际出现过的方块 id

def present_ids(snapshot_dir):
    """从快照目录里收集实际出现过的方块 id（terrain.nbt 优先，其次 memory_cells.bin）。

    只做粗提取：terrain.nbt 是 NBT，直接按 `minecraft:xxx` 正则扫；memory_cells.bin 里的
    blockId 是调色板字符串，同样正则扫。目的是"这个方块在用户世界里到底有没有"，
    不要求精确统计，宁滥勿缺。
    """
    if snapshot_dir is None:
        return None
    d = Path(snapshot_dir)
    found = set()
    for p in list(d.rglob("terrain.nbt")) + list(d.rglob("memory_cells.bin")):
        try:
            blob = p.read_bytes()
        except Exception:
            continue
        for m in re.finditer(rb"(?:minecraft:)?([a-z_]+:[a-z_0-9]+)", blob):
            found.add(m.group(1).decode("ascii", "ignore"))
    return found or None


def main():
    # Windows 控制台默认 GBK，打印 ⚠/⛔ 会 UnicodeEncodeError；统一走 UTF-8。
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass
    ap = argparse.ArgumentParser()
    ap.add_argument("--models", default=None, help="models/block 目录")
    ap.add_argument("--axis", choices=("A", "B"), default="A", help="排序轴：A=落格 ε，B=δ 窗口")
    ap.add_argument("--grep", default=None, help="子串过滤（逗号分隔，任一命中）")
    ap.add_argument("--top", type=int, default=40)
    ap.add_argument("--only-danger", action="store_true", help="只列 0<Δ<ε 的失明带（睡莲那一类）")
    ap.add_argument("--only-present", action="store_true", help="只列用户快照里出现过的方块")
    ap.add_argument("--snapshot", default=None, help="快照目录（含 terrain.nbt / memory_cells.bin）")
    args = ap.parse_args()

    root = Path(args.models) if args.models else Path(
        "decompiled_src_vf/client/assets/minecraft/models/block")
    if not root.exists():
        print(f"找不到模型目录：{root}", file=sys.stderr)
        return 2

    present = present_ids(args.snapshot) if (args.only_present or args.snapshot) else None
    if args.only_present and present is None:
        print("--only-present 需要 --snapshot <dir>（或把快照放在默认位置）", file=sys.stderr)
        return 2

    lib = ModelLib(root)
    pats = [s.strip() for s in args.grep.split(",")] if args.grep else None

    rows = []
    for name in lib.all_names():
        if pats and not any(p in name for p in pats):
            continue
        if present is not None:
            bare = name.split(":")[-1]
            block = bare[len("block/"):] if bare.startswith("block/") else bare
            if not any(block in pid or pid.endswith("minecraft:" + block) for pid in present):
                continue
        r = analyze(lib, name)
        if not r["ok"]:
            continue
        r["name"] = name
        rows.append(r)

    if args.axis == "A":
        zone_rank = {"DANGER": 0, "BOUNDARY": 1, "?": 2, "safe": 3}
        rows.sort(key=lambda r: (zone_rank[r["zone"]], r["rho"], r["h_max"]))
        head = ("排名按【A 观察侧】：DANGER（0 < Δ_min < ε，任意角度都可能失明）优先，\n"
                "  其次 BOUNDARY（Δ=0，面压在格界平面上——安全但依赖 ε 下界）\n"
                f"  ε_landing = 1/128 = {EPS_LANDING:.7f} 格；ρ = Δ_min/ε ≥ 1 即任意角度安全\n")
    else:
        rows.sort(key=lambda r: (r["z_close"], r["h_max"]))
        head = ("排名按【B 删除侧】δ 窗口闭合半径升序（越小越早进入「不可判」）\n"
                f"  c = {DELTA_NOISE_C}；z_close = sqrt(δ_cell/(c·{QUANTUM_PER_Z2:g}))\n")

    print(head)
    print(f"{'带':9s} {'模型':38s} {'h_max':>8s} {'δ_cell':>8s} {'z闭合':>7s} "
          f"{'Δ_min':>9s} {'ρ':>8s} {'失明半角':>8s}  面")
    print("-" * 116)
    shown = 0
    for r in rows:
        if shown >= args.top:
            break
        if args.only_danger and r["zone"] != "DANGER":
            continue
        shown += 1
        crit = "—安全—" if r["crit_deg"] is None else f"{r['crit_deg']:5.1f}°"
        flag = " <<<" if r["zone"] == "DANGER" else ""
        if r["z_close"] and r["z_close"] < 96:
            flag += " [δ窗<96]"
        # A 轴排序时 dmin 必有值；B 轴排序下可能为 None（该模型无「可达且可见」的面，A 轴无话可说）
        dmin_s = "     ——  " if r["dmin"] is None else f"{r['dmin']:9.6f}"
        rho_s = "    ——  " if r["rho"] is None else f"{r['rho']:8.3f}"
        face_s = (r["dmin_face"] or "—").rjust(5)
        print(f"{r['zone']:9s} {r['name']:38s} {r['h_max']:8.4f} {r['delta_cell']:8.4f} "
              f"{r['z_close']:7.1f} {dmin_s} {rho_s} {crit:>8s} "
              f"{face_s}{flag}")
    n_danger = sum(1 for r in rows if r["zone"] == "DANGER")
    n_bound = sum(1 for r in rows if r["zone"] == "BOUNDARY")
    n_delta = sum(1 for r in rows if 0 < r["z_close"] < 96)
    print(f"\n共 {len(rows)} 个含 elements 的模型（{'已按快照过滤' if present is not None else '全量'}）")
    print(f"  DANGER（0<Δ<ε，会失明）      : {n_danger}")
    print(f"  BOUNDARY（Δ=0，靠 nudge 兜底）: {n_bound}")
    print(f"  δ 窗口 < 96 格（远距不可判）  : {n_delta}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
