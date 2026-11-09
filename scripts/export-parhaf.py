#!/usr/bin/env python3
"""
Exporte le dataset Hugging Face HealthDataHub/PARHAF vers le layout standalone
attendu par le loader Java :

    data/parhaf/
      corpus.json                     index + metadonnees
      docs/<SPECIALITE>/<id>-<n>.txt  texte brut des comptes rendus

Pourquoi ce detour : la version HF est en Parquet colonnaire (les listes de
dictionnaires y sont stockees comme des dictionnaires de listes), ce qui est
penible a lire depuis Java. La version standalone documentee par le Health Data
Hub est un simple JSON + fichiers texte. Ce script produit exactement ce
format, de sorte que le loader n'ait qu'un seul contrat d'entree.

Si tu recuperes directement l'archive standalone publiee par le HDH, ce script
est inutile : pointe simplement parhaf.corpus-path sur son corpus.json.

Usage :
    pip install datasets
    python scripts/export-parhaf.py --out data/parhaf
    python scripts/export-parhaf.py --out data/parhaf --limit 50
"""

import argparse
import json
import pathlib
import re
import sys

SAFE = re.compile(r"[^A-Za-z0-9._-]+")


def slug(value: str) -> str:
    return SAFE.sub("-", (value or "x").strip())


def normalize_documents(raw):
    """
    Reconstruit une liste de dictionnaires a partir de la representation
    colonnaire de Hugging Face ({"type": [...], "text": [...]}).
    Accepte aussi la forme deja imbriquee.
    """
    if raw is None:
        return []
    if isinstance(raw, list):
        return raw
    if isinstance(raw, dict):
        keys = [k for k in raw if isinstance(raw[k], list)]
        if not keys:
            return []
        size = len(raw[keys[0]])
        return [{k: raw[k][i] for k in keys} for i in range(size)]
    return []


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default="data/parhaf")
    parser.add_argument("--dataset", default="HealthDataHub/PARHAF")
    parser.add_argument("--split", default="train")
    parser.add_argument("--limit", type=int, default=0)
    parser.add_argument("--specialty", action="append", default=[])
    args = parser.parse_args()

    try:
        from datasets import load_dataset
    except ImportError:
        print("Il manque la librairie datasets :  pip install datasets", file=sys.stderr)
        return 1

    out = pathlib.Path(args.out)
    docs_root = out / "docs"
    docs_root.mkdir(parents=True, exist_ok=True)

    print(f"Telechargement de {args.dataset} ...")
    dataset = load_dataset(args.dataset, split=args.split)

    records, written = [], 0
    for row in dataset:
        specialty = row.get("specialty") or "INCONNU"
        if args.specialty and specialty not in args.specialty:
            continue

        documents, index = [], 0
        for doc in normalize_documents(row.get("documents")):
            text = doc.get("text")
            if not text:
                continue
            spec_dir = docs_root / slug(specialty)
            spec_dir.mkdir(parents=True, exist_ok=True)

            filename = f"{slug(row['id'])}-{index}.txt"
            (spec_dir / filename).write_text(text, encoding="utf-8")

            documents.append({
                "type": doc.get("type"),
                "header": doc.get("header"),
                "word_count": doc.get("word_count"),
                # chemin relatif au corpus.json, comme le format standalone
                "path": f"docs/{slug(specialty)}/{filename}",
            })
            index += 1
            written += 1

        if not documents:
            continue

        records.append({
            "id": row.get("id"),
            "local_id": row.get("local_id"),
            "specialty": specialty,
            "author": row.get("author"),
            "reviewer": row.get("reviewer"),
            "pool": row.get("pool"),
            "suggested_scenario": row.get("suggested_scenario"),
            "structured_abstract": row.get("structured_abstract"),
            "documents": documents,
        })

        if args.limit and len(records) >= args.limit:
            break

    corpus_file = out / "corpus.json"
    corpus_file.write_text(
        json.dumps({"data": records}, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    print(f"OK : {len(records)} patients, {written} comptes rendus")
    print(f"     index  -> {corpus_file}")
    print(f"     textes -> {docs_root}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
