"""Fixed-end-pose image gate; run only after clients have stopped. Requires Pillow and NumPy."""
from pathlib import Path
import json
import numpy as np
from PIL import Image

root = Path.cwd()
results = {}
for phase in ['steady', 'pan', 'sort', 'fly']:
    images = [np.asarray(Image.open(root/f'run/screenshots/alacer-mg-shot-{arm}-r2-vis_on-ecl_on-ent0-{phase}.png').convert('RGB'), dtype=np.int16)
              for arm in ['a1', 'a2', 'b1']]
    assert all(a.shape == (1080, 1920, 3) for a in images)
    def fraction(a, b):
        return float(np.mean(np.max(np.abs(a-b), axis=2) > 3))
    noise = fraction(images[0], images[1])
    candidate = fraction(images[0], images[2])
    limit = max(.001, 2*noise)
    results[phase] = {'control_control_fraction': noise, 'candidate_control_fraction': candidate,
                      'limit_fraction': limit, 'passed': candidate <= limit}
path = Path(__file__).resolve().parent/'image-comparison.json'
path.write_text(json.dumps(results, indent=2)+'\n')
print(json.dumps(results, indent=2))
