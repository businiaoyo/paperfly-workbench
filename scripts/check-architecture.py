#!/usr/bin/env python3
"""Keep configuration, persistence and layout independent of the live Android window host."""
from pathlib import Path
import re
import sys

root = Path(__file__).resolve().parents[1] / 'app/src/main/java/cn/paperfly/workbench'
errors = []
android_storage = {
    'android.content.Context', 'android.content.SharedPreferences', 'android.util.AtomicFile',
    'android.os.Handler', 'android.os.Looper',
}
for path in sorted(root.rglob('*.java')):
    rel = path.relative_to(root)
    imports = re.findall(r'^import\s+([\w.]+);', path.read_text(), re.MULTILINE)
    for dependency in imports:
        if rel.parts[0] == 'model':
            allowed = dependency.startswith(('java.', 'cn.paperfly.workbench.model.'))
        elif rel.parts[0] == 'data':
            allowed = dependency.startswith(('java.', 'org.json.', 'cn.paperfly.workbench.model.',
                                              'cn.paperfly.workbench.data.')) or dependency in android_storage
        elif path.name in {'StageLayout.java', 'StagePresentation.java', 'CardSession.java', 'PaneLayout.java', 'WindowLoadQueue.java', 'WindowCanvasPolicy.java', 'TaskHandoff.java', 'TaskRestore.java', 'EntryRoute.java', 'AdbEndpointIdentity.java', 'AdbEndpointCatalog.java', 'AdbPrerequisites.java', 'DockModel.java', 'MultitouchGesture.java'}:
            allowed = dependency.startswith(('java.', 'cn.paperfly.workbench.model.'))
        else:
            continue
        if not allowed:
            errors.append(f'{rel}: invalid layer dependency {dependency}')

for name in ['MainActivity.java', 'StageActivity.java']:
    text = (root / name).read_text()
    if re.search(r'\.(?:get|put)String\("components"', text):
        errors.append(f'{name}: legacy component storage bypasses WorkspaceRepository')

if errors:
    print('\n'.join(errors), file=sys.stderr)
    sys.exit(1)
print('Architecture boundaries passed: model, storage, layout and legacy persistence access.')
