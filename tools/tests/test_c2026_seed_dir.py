import json
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "c2026"))


def test_public_seed_lines_skips_non_seed_files(tmp_path):
    """tools/seed 에 노선 seed 가 아닌 파일(ko_wiki.json 같은 목록)·한국 seed 가 있어도 일본 노선만 읽는다"""
    import importlib.util
    path = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "c2026", "build_seed.py")
    spec = importlib.util.spec_from_file_location("c2026_build_seed", path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    (tmp_path / "ko_wiki.json").write_text(json.dumps([["新宿", 35.69, 139.7, "신주쿠"]]), encoding="utf-8")
    (tmp_path / "seoul.json").write_text(json.dumps({"lines": [{"id": "kr.Seoul.2", "stations": []}]}), encoding="utf-8")
    (tmp_path / "tokyo.json").write_text(json.dumps({"lines": [{"id": "odpt.Railway:TokyoMetro.Ginza", "stations": []}]}), encoding="utf-8")
    (tmp_path / "notes.txt").write_text("x", encoding="utf-8")
    assert [L["id"] for L in mod.public_seed_lines(str(tmp_path))] == ["odpt.Railway:TokyoMetro.Ginza"]


def test_real_seed_dir_is_readable():
    """실제 tools/seed 폴더를 읽어도 오류가 없고 일본 노선이 나온다 (릴리즈 빌드에서만 도는 경로를 평소 CI 에서도 검사)"""
    import importlib.util
    tools = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    spec = importlib.util.spec_from_file_location("c2026_build_seed2", os.path.join(tools, "c2026", "build_seed.py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    ids = [L["id"] for L in mod.public_seed_lines(os.path.join(tools, "seed"))]
    assert ids and all(i.startswith("odpt.") for i in ids)
