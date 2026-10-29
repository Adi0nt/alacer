"""Reuse the previous retained result extractor for this separate evidence directory."""
from pathlib import Path
import importlib.util

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('alacer_previous_functional_analysis',
    HERE.parent / '2026-10-29-remaining-features/analyze.py')
analysis = importlib.util.module_from_spec(spec)
spec.loader.exec_module(analysis)
analysis.HERE = HERE
if __name__ == '__main__': analysis.main()
