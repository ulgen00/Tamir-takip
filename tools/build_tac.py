#!/usr/bin/env python3
"""IMEI -> marka/model veritabanini (tac/*.json) olusturur.

Kaynak: https://github.com/MoazEb/tac-database (MIT lisansi) - tac_full.csv
Kullanim:  python3 tools/build_tac.py [tac_full.csv]
Dosya verilmezse GitHub'dan indirilir.
"""
import csv, collections, io, json, os, shutil, sys, urllib.request

SRC = "https://raw.githubusercontent.com/MoazEb/tac-database/main/tac_full.csv"
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "tac")
P = 4  # parca dosyasi = TAC'in ilk 4 hanesi

def main():
    if len(sys.argv) > 1:
        text = open(sys.argv[1], encoding="utf-8").read()
    else:
        text = urllib.request.urlopen(SRC, timeout=120).read().decode("utf-8")
    shards = collections.defaultdict(dict)
    for row in csv.DictReader(io.StringIO(text)):
        tac, spec = (row.get("TAC") or "").strip(), (row.get("SPECS") or "").strip()
        if not tac.isdigit() or len(tac) < 6 or not spec:
            continue
        tac = tac.zfill(8)
        shards[tac[:P]][tac[P:]] = spec
    shutil.rmtree(OUT, ignore_errors=True)
    os.makedirs(OUT)
    total = 0
    for pre, d in sorted(shards.items()):
        models = sorted(set(d.values()))
        ix = {m: i for i, m in enumerate(models)}
        data = {"m": models, "t": {k: ix[v] for k, v in sorted(d.items())}}
        with open(os.path.join(OUT, pre + ".json"), "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, separators=(",", ":"))
        total += len(d)
    with open(os.path.join(OUT, "index.json"), "w", encoding="utf-8") as f:
        json.dump({"p": P, "n": total, "s": sorted(shards)}, f, separators=(",", ":"))
    with open(os.path.join(OUT, "LICENSE"), "w", encoding="utf-8") as f:
        f.write("Data derived from https://github.com/MoazEb/tac-database\n\n"
                + urllib.request.urlopen(SRC.replace("tac_full.csv", "LICENSE"), timeout=60).read().decode("utf-8"))
    print(f"{total} TAC, {len(shards)} dosya -> {os.path.normpath(OUT)}")

if __name__ == "__main__":
    main()
