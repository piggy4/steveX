"""极简 NBT 读取（只读，够用即可）：供一次性诊断脚本 import。"""
import gzip, struct, io

def _rd(fmt, f):
    return struct.unpack(fmt, f.read(struct.calcsize(fmt)))[0]

def _payload(t, f):
    if t == 1: return _rd('>b', f)
    if t == 2: return _rd('>h', f)
    if t == 3: return _rd('>i', f)
    if t == 4: return _rd('>q', f)
    if t == 5: return _rd('>f', f)
    if t == 6: return _rd('>d', f)
    if t == 7:
        n = _rd('>i', f); return f.read(n)
    if t == 8:
        n = _rd('>H', f); return f.read(n).decode('utf-8', 'replace')
    if t == 9:
        et = f.read(1)[0]; n = _rd('>i', f)
        return [_payload(et, f) for _ in range(n)]
    if t == 10:
        out = {}
        while True:
            tt = f.read(1)[0]
            if tt == 0: return out
            nl = _rd('>H', f)
            # ★ 必须先读名再读值：`out[f.read(nl)...] = _payload(tt, f)` 会被 CPython 求值成
            #   "先算右值、再算下标"——那样 payload 从名字的字节开始解析，整个文件错位。
            #   （与本项目 MemoryCellsReader 的字段序 bug 同类：读方的读取次序必须与写方一致。）
            key = f.read(nl).decode('utf-8', 'replace')
            out[key] = _payload(tt, f)
    if t == 11:
        n = _rd('>i', f); return [_rd('>i', f) for _ in range(n)]
    if t == 12:
        n = _rd('>i', f); return [_rd('>q', f) for _ in range(n)]
    raise ValueError(f'未知 tag {t}')

def load(path):
    b = open(path, 'rb').read()
    if b[:2] == b'\x1f\x8b': b = gzip.decompress(b)
    f = io.BytesIO(b)
    t = f.read(1)[0]
    if t == 0: return None, None
    nl = _rd('>H', f)
    return f.read(nl).decode('utf-8', 'replace'), _payload(t, f)
