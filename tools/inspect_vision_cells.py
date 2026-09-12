#!/usr/bin/env python3
"""v2.37 离线解剖器：把记忆侧写出的 memory_cells.bin / memory_sprites.bin 解析成人可读形式。

用途（诊断"某个非满形状方块删不掉"时走这条路，无需改代码、无需重启游戏）：
  1. 目标格到底有没有进几何段？blockId / quad 数是多少？
  2. 顶点与 sprite 局部 UV 的数值是否合理（是否出现 NaN / 越界 / 全 0）？
  3. spriteIndex 指向的 sprite 叫什么？其 alpha 掩码是 OPAQUE 还是实表？非零 texel 有多少？
  4. 两个文件的 epoch 是否相等（不等则采集侧整段作废）？

用法：
  python inspect_vision_cells.py <memory_cells.bin> [--sprites <memory_sprites.bin>]
  python inspect_vision_cells.py <dir>                       # 自动找同目录下两个文件
  python inspect_vision_cells.py <memory_cells.bin> --find 12,64,-30    # 定位某个方块坐标
"""

import argparse
import struct
import sys
from collections import Counter
from pathlib import Path

CELLS_MAGIC = b"SCEL"
SPRITES_MAGIC = b"SSPR"
FLAG_OPAQUE, FLAG_RLE, FLAG_ANIMATED, FLAG_INVALID = 0b001, 0b010, 0b100, 0b1000
QUAD_BYTES = (12 + 8 + 3) * 4 + 4  # 96


def _signed(field, bits):
    """从无符号字段取出有符号值（二补数）。

    注意：Python 整数是任意精度，"左移 N 位再右移 N 位"**不是**截断（会原样返回），
    必须显式按位掩码后再补符号位。
    """
    return (field ^ (1 << (bits - 1))) - (1 << (bits - 1))


def unpack_pos(v):
    """BlockPos.asLong 解包：`x<<38 | z<<12 | y`（x/z 各 26 位、y 12 位，均为有符号）。

    已用实测数据校验：按本式解出的 y 落在 [47,73]（地形高度），另一种候选布局
    (`x<<38|y<<12|z`) 解出的 y 落在 [-58,122] —— 前者才是竖直轴。
    """
    u = v & 0xFFFFFFFFFFFFFFFF
    return (_signed((u >> 38) & 0x3FFFFFF, 26),
            _signed(u & 0xFFF, 12),
            _signed((u >> 12) & 0x3FFFFFF, 26))


def parse_sprites(path):
    b = Path(path).read_bytes()
    if b[:4] != SPRITES_MAGIC:
        raise SystemExit(f"{path}: 魔数不是 SSPR（{b[:4]!r}）")
    if b[4] != 1:
        raise SystemExit(f"{path}: 未知版本 {b[4]}")
    epoch, count = struct.unpack_from("<qI", b, 5)
    p = 17
    entries = []
    for i in range(count):
        (nlen,) = struct.unpack_from("<H", b, p)
        p += 2
        name = b[p:p + nlen].decode("utf-8", "replace")
        p += nlen
        w, h = struct.unpack_from("<HH", b, p)
        flags = b[p + 4]
        p += 5
        # payload 的有无**只由** OPAQUE / INVALID 决定，与 ANIMATED 无关——写方对 ANIMATED 条目
        # 照样写 payload。若因 ANIMATED 提前 continue，游标会错位、其后每个条目都读到垃圾。
        alpha = None
        if flags & FLAG_INVALID:
            pass                       # 占位条目：0 尺寸、无 payload
        elif flags & FLAG_OPAQUE:
            alpha = bytes([255]) * (w * h)
        elif flags & FLAG_RLE:
            (rlen,) = struct.unpack_from("<i", b, p)
            p += 4
            out = bytearray()
            for k in range(0, rlen, 2):
                out += bytes([b[p + k]]) * b[p + k + 1]
            p += rlen
            alpha = bytes(out)
        else:
            alpha = b[p:p + w * h]
            p += w * h
        if flags & FLAG_ANIMATED:
            alpha = None                # 不可用（帧带布局与 [0,1] UV 不对应），但 payload 已消费
        entries.append({
            "index": i, "name": name, "w": w, "h": h, "flags": flags, "alpha": alpha,
        })
    return {"epoch": epoch, "count": count, "entries": entries, "consumed": p, "total": len(b)}


def parse_cells(path):
    b = Path(path).read_bytes()
    if b[:4] != CELLS_MAGIC:
        raise SystemExit(f"{path}: 魔数不是 SCEL（{b[:4]!r}）")
    ver = b[4]
    if ver != 4:
        raise SystemExit(f"{path}: 本脚本只解析 v4（实际 {ver}）。"
                         f"v<4 说明记忆侧还没写几何段——那本身就是结论。")
    p = 5
    (dlen,) = struct.unpack_from("<i", b, p)
    p += 4
    dim = b[p:p + dlen].decode("utf-8", "replace")
    p += dlen
    threshold, maxdist = struct.unpack_from("<id", b, p)
    p += 12
    (opaque_count,) = struct.unpack_from("<i", b, p)
    p += 4
    main = [unpack_pos(v) for v in struct.unpack_from(f"<{opaque_count}q", b, p)]
    p += opaque_count * 8
    t_enabled = b[p] != 0
    p += 1
    (t_count,) = struct.unpack_from("<i", b, p)
    p += 4
    translucent = [unpack_pos(v) for v in struct.unpack_from(f"<{t_count}q", b, p)]
    p += t_count * 8
    (sprite_epoch,) = struct.unpack_from("<q", b, p)
    p += 8
    (shaped_count,) = struct.unpack_from("<i", b, p)
    p += 4

    shaped = []
    for i in range(shaped_count):
        (posl,) = struct.unpack_from("<q", b, p)
        p += 8
        (idlen,) = struct.unpack_from("<H", b, p)
        p += 2
        block_id = b[p:p + idlen].decode("utf-8", "replace")
        p += idlen
        quad_count = b[p]
        p += 1
        quads = []
        for _ in range(quad_count):
            floats = struct.unpack_from("<23f", b, p)
            sprite_index = struct.unpack_from("<i", b, p + 92)[0]
            p += QUAD_BYTES
            quads.append({
                "v": [floats[0:3], floats[3:6], floats[6:9], floats[9:12]],
                "uv": [floats[12:14], floats[14:16], floats[16:18], floats[18:20]],
                "n": floats[20:23],
                "sprite": sprite_index,
            })
        shaped.append({"pos": unpack_pos(posl), "posl": posl, "id": block_id, "quads": quads})

    return {
        "version": ver, "dim": dim, "threshold": threshold, "maxdist": maxdist,
        "main": main, "translucent": translucent, "t_enabled": t_enabled,
        "sprite_epoch": sprite_epoch, "shaped": shaped, "consumed": p, "total": len(b),
    }


def fmt_float(f):
    if f != f:
        return "NaN"
    if f in (float("inf"), float("-inf")):
        return "inf"
    return f"{f:+.4f}"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("cells")
    ap.add_argument("--sprites")
    ap.add_argument("--find", help="定位坐标 x,y,z（逗号分隔）")
    ap.add_argument("--id", help="只统计 blockId 含该子串的格")
    ap.add_argument("--quads", action="store_true", help="打印每格的 quad 明细")
    args = ap.parse_args()

    cells_path = Path(args.cells)
    if cells_path.is_dir():
        cells_path = cells_path / "memory_cells.bin"
    sprites_path = Path(args.sprites) if args.sprites else cells_path.parent / "memory_sprites.bin"

    c = parse_cells(cells_path)
    print(f"=== {cells_path} ===")
    print(f"version={c['version']} dim={c['dim']!r} pixelThreshold={c['threshold']} "
          f"maxRayDist={c['maxdist']}")
    print(f"main={len(c['main'])} translucent={len(c['translucent'])} "
          f"(enabled={c['t_enabled']}) shaped={len(c['shaped'])}")
    print(f"cells.spriteEpoch={c['sprite_epoch']}")
    print(f"解析消耗 {c['consumed']} / 文件 {c['total']} 字节"
          + ("  ✅" if c["consumed"] == c["total"] else "  ⚠️ 不一致（可能有残留/截断）"))

    sprites = None
    if sprites_path.exists():
        sprites = parse_sprites(sprites_path)
        print(f"\n=== {sprites_path} ===")
        print(f"epoch={sprites['epoch']} count={sprites['count']} "
              f"(文件 {sprites['total']} 字节，解析到 {sprites['consumed']})")
        print(f"epoch 是否与 cells 一致: "
              + ("✅ 一致" if sprites["epoch"] == c["sprite_epoch"] else "❌ 不一致 ⇒ 采集侧整段作废"))
    else:
        print(f"\n⚠️ 找不到 {sprites_path} —— 采集侧 hasEpoch() 必为 false，几何段整段作废")

    # δ 的逐格化口径（§3.2 2b-δ / §7 决策 O）：判据 2b 要成立必须有 δ < h_max，
    # 其中 h_max = 该格 quad 顶点在格局部的最大 y。δ_global = 0.05 是旧口径。
    DELTA_GLOBAL, KAPPA = 0.05, 0.5

    print("\n=== 几何段 blockId 分布 ===")
    print("  h_max = 格内顶点的最大 y；δ_cell = min(0.05, 0.5·h_max)。"
          "h_max <= 0.05 ⇒ 旧口径下判据 2b 按构造不可满足（§3.2 2b-δ）")
    counter = Counter(s["id"] for s in c["shaped"])
    if not counter:
        print("  （几何段为空！记忆侧一个非满形状格都没上报——先查记忆侧）")
    for bid, n in counter.most_common():
        cells = [s for s in c["shaped"] if s["id"] == bid]
        quad_total = sum(len(s["quads"]) for s in cells)
        y_min = min(min(p[1] for p in q["v"]) for s in cells for q in s["quads"])
        y_max = max(max(p[1] for p in q["v"]) for s in cells for q in s["quads"])
        flat = all(abs(q["n"][1]) > 0.99 for s in cells for q in s["quads"])
        d_cell = min(DELTA_GLOBAL, KAPPA * y_max)
        # 采集侧算的 δ 是**逐格**的（同种方块的变体模型可能不同高）。这里按 blockId 取组内最大
        # h_max ⇒ 本列是"该方块可得的最好情况"；若同一 id 有多种高度，实际有的格 δ 更小。
        # y 范围一栏就是给这个情形看的：`y` 上下界不相等且中段有跳变 ⇒ 该 id 混了多个模型。
        flag = "" if y_max > DELTA_GLOBAL else "  ← ❌ 旧口径永不删（本次修订对象）"
        print(f"  {bid:<45} 格数={n:<5} quad 总数={quad_total:<6} "
              f"y=[{y_min:.4f},{y_max:.4f}] h_max={y_max:.4f} "
              f"δ_cell={d_cell:.4f} {'水平平板' if flat else '立体':<8}{flag}")

    if sprites:
        print("\n=== sprite 表 ===")
        for e in sprites["entries"]:
            tag = []
            if e["flags"] & FLAG_INVALID:
                tag.append("INVALID(占位)")
            if e["flags"] & FLAG_ANIMATED:
                tag.append("ANIMATED(采集侧作废)")
            if e["flags"] & FLAG_OPAQUE:
                tag.append("OPAQUE")
            if e["flags"] & FLAG_RLE:
                tag.append("RLE")
            a = e["alpha"]
            if a:
                zero = sum(1 for x in a if x == 0)
                half = sum(1 for x in a if 0 < x < 128)
                full = sum(1 for x in a if x >= 128)
                stat = f"alpha: 0={zero} mid={half} >=128={full} / {len(a)}"
            elif a is None:
                stat = "无 alpha（占位或动画）"
            else:
                stat = "全 OPAQUE"
            print(f"  [{e['index']:>3}] {e['name']:<45} {e['w']}x{e['h']:<5} {'|'.join(tag):<28} {stat}")

    if args.id:
        print(f"\n=== blockId 含 {args.id!r} 的格 ===")
        hits = [s for s in c["shaped"] if args.id in s["id"]]
        print(f"  命中 {len(hits)} 格")
        for s in hits[:40]:
            print(f"  {s['pos']} {s['id']} quads={len(s['quads'])}")
        if not hits:
            print("  ❌ 几何段里没有这个方块 —— 记忆侧没上报（见下面的排查清单）")

    if args.find:
        x, y, z = (int(v) for v in args.find.split(","))
        print(f"\n=== 定位 {x},{y},{z} ===")
        hit = [s for s in c["shaped"] if s["pos"] == (x, y, z)]
        if not hit:
            print("  ❌ 该坐标不在几何段里")
            print(f"     在 main 段: { (x,y,z) in c['main'] }")
            print(f"     在 translucent 段: { (x,y,z) in c['translucent'] }")
        for s in hit:
            print(f"  blockId={s['id']} quads={len(s['quads'])}")
            for i, q in enumerate(s["quads"]):
                sprite_name = "?"
                if sprites and 0 <= q["sprite"] < len(sprites["entries"]):
                    sprite_name = sprites["entries"][q["sprite"]]["name"]
                print(f"    quad[{i}] n=({fmt_float(q['n'][0])},{fmt_float(q['n'][1])},"
                      f"{fmt_float(q['n'][2])}) spriteIdx={q['sprite']} ({sprite_name})")
                for j, v in enumerate(q["v"]):
                    print(f"      p{j}=({fmt_float(v[0])}, {fmt_float(v[1])}, {fmt_float(v[2])})"
                          f"  uv{j}=({fmt_float(q['uv'][j][0])}, {fmt_float(q['uv'][j][1])})")

    if args.quads:
        print("\n=== 全部几何段的 quad 明细（前 20 格）===")
        for s in c["shaped"][:20]:
            print(f"  {s['pos']} {s['id']}")
            for i, q in enumerate(s["quads"]):
                vmin = [min(p[k] for p in q["v"]) for k in range(3)]
                vmax = [max(p[k] for p in q["v"]) for k in range(3)]
                print(f"    quad[{i}] sprite={q['sprite']} 局部盒 "
                      f"[{fmt_float(vmin[0])},{fmt_float(vmax[0])}]x"
                      f"[{fmt_float(vmin[1])},{fmt_float(vmax[1])}]x"
                      f"[{fmt_float(vmin[2])},{fmt_float(vmax[2])}]")


if __name__ == "__main__":
    sys.exit(main())
