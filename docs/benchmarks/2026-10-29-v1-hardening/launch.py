"""Use the retained functional launcher with this campaign's separate evidence directory."""
from pathlib import Path
import importlib.util

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('alacer_previous_functional_launcher',
    HERE.parent / '2026-10-29-remaining-features/launch.py')
previous = importlib.util.module_from_spec(spec)
spec.loader.exec_module(previous)

previous.HERE = HERE
ROOT, JDK, sha = previous.ROOT, previous.JDK, previous.sha
if __name__ == '__main__': previous.main()
