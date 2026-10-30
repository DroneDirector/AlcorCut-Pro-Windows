#!/usr/bin/env python3
"""
Brings the shared code over from the Android project.

  python3 tools/sync.py <path to Android-Projects/ProCamera>

tools/sync.txt lists what comes over:
  copy <file>                     the whole file, unchanged
  take <file> <Name> [<Name>...]  only those top-level declarations (with their comments),
                                  for files that also hold phone-only parts
  drop <file> <member> [...]      remove member functions (by name) from a file already brought over,
                                  where they only serve the phone (e.g. video parts of a photo tool)
  edit <file> <python file>       after copying, apply replacements from tools/edits/<python file>
  swap <file> <Name> <text file>  replace one top-level declaration with tools/edits/<text file>
  asset <file>                    copy a bundled file from the app's assets into src/main/resources
Files written by this tool start from the Android version every time, so changes belong in
tools/edits, never in the copied files.
"""
import os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PKG = 'com/dronedirector/veyrocutpro'
SRC = os.path.join(ROOT, 'src/main/kotlin', PKG)


def top_blocks(text):
    """Top-level declarations: name -> (start line, end line) incl. preceding comments/annotations."""
    lines = text.split('\n')
    decl = re.compile(r'^(?:(?:private|internal|public|data|enum|sealed|abstract|open|inline|value|annotation|suspend|operator|infix|const|lateinit)\s+)*'
                      r'(?:(?:class|object|interface|typealias|fun interface)\s+([A-Za-z_]\w*)'
                      r'|fun\s+(?:<[^>]*>\s*)?(?:[\w.<>?*]+(?:<[^>]*>)?\??\.)?([A-Za-z_]\w*)'
                      r'|(?:val|var)\s+(?:<[^>]*>\s*)?(?:[\w.<>?*]+\.)?([A-Za-z_]\w*))')
    starts = []
    for i, l in enumerate(lines):
        if l[:1] in ('', ' ', '\t', '}', ')', ']'):
            continue
        m = decl.match(l)
        if m:
            starts.append((i, m.group(1) or m.group(2) or m.group(3)))
    out = {}
    for k, (i, name) in enumerate(starts):
        s = i
        while s > 0 and (lines[s - 1].startswith(('/**', ' *', '@', '//')) or lines[s - 1].startswith('*/')):
            s -= 1
        e = i + 1
        while e < len(lines) and not (lines[e][:1] not in ('', ' ', '\t', '}', ')', ']')):
            e += 1
        # Back off over the next declaration's comment block and blank lines.
        while e > i + 1 and (lines[e - 1].strip() == '' or lines[e - 1].startswith(('/**', ' *', '@', '//', '*/'))):
            e -= 1
        out.setdefault(name, []).append((s, e))
    return lines, out


def take(text, names):
    lines, blocks = top_blocks(text)
    head = []
    body = []
    for l in lines:
        if l.startswith('package '):
            head.append(l)
    imports = [l for l in lines if l.startswith('import ')]
    for n in names:
        if n not in blocks:
            sys.exit('take: %s not found' % n)
        for s, e in blocks[n]:
            body.append('\n'.join(lines[s:e]).rstrip())
    joined = '\n\n'.join(body)
    used = []
    for imp in imports:
        name = imp.split(' as ')[-1].split('.')[-1].strip()
        if re.search(r'\b%s\b' % re.escape(name), joined) or (name in ('getValue', 'setValue') and ' by ' in joined):
            used.append(imp)
    return '\n'.join(head) + '\n\n' + '\n'.join(used) + '\n\n' + joined + '\n'


def drop(text, names):
    lines = text.split('\n')
    for n in names:
        pat = re.compile(r'^(\s+)(?:@\w+(?:\([^)]*\))?\s+)*(?:(?:private|internal|public|override|suspend|inline|operator)\s+)*fun\s+(?:<[^>]*>\s*)?%s\s*\(' % re.escape(n))
        found = False
        for i, l in enumerate(lines):
            m = pat.match(l)
            if not m:
                continue
            ind = m.group(1)
            s = i
            while s > 0 and lines[s - 1].strip().startswith(('/**', '*', '*/', '@', '//')) and lines[s - 1].startswith(ind):
                s -= 1
            depth = 0; e = i; opened = False
            while e < len(lines):
                code = re.sub(r'"(?:\\.|[^"\\])*"', '""', lines[e])
                code = re.sub(r"'(?:\\.|[^'\\])'", "''", code).split('//')[0]
                depth += code.count('{') - code.count('}')
                opened = opened or '{' in code
                e += 1
                if depth <= 0 and (opened or not code.rstrip().endswith(('=', '(', ','))):
                    if depth <= 0 and (opened or e > i):
                        break
            del lines[s:e]
            found = True
            break
        if not found:
            sys.exit('drop: %s not found' % n)
    return '\n'.join(lines)


def main():
    android = os.path.join(sys.argv[1], 'app/src/main/java', PKG)
    edits_dir = os.path.join(ROOT, 'tools/edits')
    done = []
    for raw in open(os.path.join(ROOT, 'tools/sync.txt'), encoding='utf-8'):
        line = raw.split('#', 1)[0].strip()
        if not line:
            continue
        parts = line.split()
        cmd, f = parts[0], parts[1]
        if cmd == 'assetdir':
            import shutil
            d = f
            srcd = os.path.join(sys.argv[1], 'app/src/main/assets', d)
            dstd = os.path.join(ROOT, 'src/main/resources', d)
            os.makedirs(dstd, exist_ok=True)
            names = sorted(n for n in os.listdir(srcd) if os.path.isfile(os.path.join(srcd, n)))
            for n in names:
                shutil.copyfile(os.path.join(srcd, n), os.path.join(dstd, n))
            open(os.path.join(dstd, 'index.txt'), 'w').write('\n'.join(names) + '\n')
            continue
        if cmd == 'asset':
            import shutil
            dst = os.path.join(ROOT, 'src/main/resources', f)
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            shutil.copyfile(os.path.join(sys.argv[1], 'app/src/main/assets', f), dst)
            continue
        src = os.path.join(android, f)
        dst = os.path.join(SRC, f)
        if cmd == 'copy':
            text = open(src, encoding='utf-8').read()
        elif cmd == 'take':
            text = take(open(src, encoding='utf-8').read(), parts[2:])
        elif cmd == 'swap':
            text = open(dst, encoding='utf-8').read()
            lines, blocks = top_blocks(text)
            if parts[2] not in blocks:
                sys.exit('swap: %s not found' % parts[2])
            s0, e0 = blocks[parts[2]][0]
            repl = open(os.path.join(edits_dir, parts[3]), encoding='utf-8').read().rstrip('\n').split('\n')
            text = '\n'.join(lines[:s0] + repl + lines[e0:])
        elif cmd == 'drop':
            text = drop(open(dst, encoding='utf-8').read(), parts[2:])
        elif cmd == 'edit':
            text = open(dst, encoding='utf-8').read()
            rules = {}
            exec(open(os.path.join(edits_dir, parts[2]), encoding='utf-8').read(), rules)
            for old, new in rules['EDITS']:
                if old not in text:
                    sys.exit('edit %s: not found: %r' % (f, old[:80]))
                text = text.replace(old, new)
        else:
            sys.exit('unknown: ' + cmd)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        open(dst, 'w', encoding='utf-8').write(text)
        done.append(f)
    print('%d files' % len(set(done)))


if __name__ == '__main__':
    main()
