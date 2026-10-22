"""Regenerate the repeated cloud A/B evidence without overwriting the session 7 tables."""
import importlib.util
import pathlib

here = pathlib.Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("cloud_analysis", here.parent / "2026-10-19-cloud-cache/analyze.py")
analysis = importlib.util.module_from_spec(spec)
spec.loader.exec_module(analysis)
analysis.DEST = here
analysis.main(["cc-on-r1", "cc-on-r2", "cc-on-r3"], prefix="cloud-")
