"""用 Whisper 识别课文音频里每个词的时间，再和课文文字对齐，得到每一句的起止时间。

用法：python align.py --server http://.../res/ --unit english/pep3a/u1/ --out align.json
输出：每课每句的 start/end、匹配率，以及识别出来的原始文字（供人工核对）。
"""
import argparse, difflib, json, os, re, urllib.request

from faster_whisper import WhisperModel


def norm(w):
    return re.sub(r"[^a-z0-9']", "", w.lower().replace("’", "'"))


NUM = {"1": "one", "2": "two", "3": "three", "4": "four", "5": "five", "6": "six"}


def ref_tokens(text):
    out = []
    for w in re.split(r"[\s\-]+", text):
        n = norm(w)
        if not n:
            continue
        out.append(NUM.get(n, n))
    return out


def sim(a, b):
    if a == b:
        return 2.0
    a2, b2 = a.replace("'", ""), b.replace("'", "")
    if a2 == b2:
        return 2.0
    r = difflib.SequenceMatcher(None, a2, b2).ratio()
    return 1.0 if r >= 0.7 else -1.0


def align(ref, hyp, gap_ref=-1.0, gap_hyp=-0.3):
    """全局对齐：ref 是课文词，hyp 是识别词。识别多出来的词（标题、重复）罚分很小。"""
    n, m = len(ref), len(hyp)
    S = [[0.0] * (m + 1) for _ in range(n + 1)]
    B = [[0] * (m + 1) for _ in range(n + 1)]
    for i in range(1, n + 1):
        S[i][0] = S[i - 1][0] + gap_ref; B[i][0] = 1
    for j in range(1, m + 1):
        S[0][j] = S[0][j - 1] + gap_hyp; B[0][j] = 2
    for i in range(1, n + 1):
        for j in range(1, m + 1):
            d = S[i - 1][j - 1] + sim(ref[i - 1], hyp[j - 1])
            u = S[i - 1][j] + gap_ref
            l = S[i][j - 1] + gap_hyp
            if d >= u and d >= l:
                S[i][j], B[i][j] = d, 0
            elif u >= l:
                S[i][j], B[i][j] = u, 1
            else:
                S[i][j], B[i][j] = l, 2
    pairs = {}
    i, j = n, m
    while i > 0 or j > 0:
        b = B[i][j]
        if i > 0 and j > 0 and b == 0:
            if sim(ref[i - 1], hyp[j - 1]) > 0:
                pairs[i - 1] = j - 1
            i, j = i - 1, j - 1
        elif i > 0 and (j == 0 or b == 1):
            i -= 1
        else:
            j -= 1
    return pairs


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--server", required=True)
    ap.add_argument("--unit", required=True)
    ap.add_argument("--out", default="align.json")
    ap.add_argument("--model", default="small.en")
    a = ap.parse_args()
    base = a.server.rstrip("/") + "/" + a.unit.strip("/") + "/"
    unit = json.loads(urllib.request.urlopen(base + "unit.json").read().decode("utf-8"))
    model = WhisperModel(a.model, device="cpu", compute_type="int8")
    os.makedirs("audio", exist_ok=True)
    result = {}
    for L in unit["lessons"]:
        local = os.path.join("audio", L["id"] + ".mp3")
        urllib.request.urlretrieve(base + L["audio"], local)
        segs, _ = model.transcribe(local, language="en", word_timestamps=True, beam_size=5,
                                   vad_filter=False, condition_on_previous_text=False)
        words = []
        for s in segs:
            for w in s.words or []:
                n = norm(w.word)
                if n:
                    words.append((NUM.get(n, n), round(w.start, 2), round(w.end, 2), w.word.strip()))
        hyp = [w[0] for w in words]
        ref, owner = [], []
        for si, s in enumerate(L["sentences"]):
            for t in ref_tokens(s["en"]):
                ref.append(t); owner.append(si)
        pairs = align(ref, hyp) if ref else {}
        out = []
        for si, s in enumerate(L["sentences"]):
            idx = [k for k in range(len(ref)) if owner[k] == si]
            hit = [pairs[k] for k in idx if k in pairs]
            item = {"en": s["en"], "n": len(idx), "matched": len(hit)}
            if hit:
                item["start"] = words[min(hit)][1]
                item["end"] = words[max(hit)][2]
                item["heard"] = " ".join(words[h][3] for h in range(min(hit), max(hit) + 1))
            out.append(item)
        result[L["id"]] = {"sentences": out, "transcript": " ".join(f"{w[3]}@{w[1]}" for w in words)}
        print(L["id"], f"{sum(o['matched'] for o in out)}/{sum(o['n'] for o in out)}")
    json.dump(result, open(a.out, "w"), ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
