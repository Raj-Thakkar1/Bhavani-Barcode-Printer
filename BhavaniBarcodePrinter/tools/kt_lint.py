#!/usr/bin/env python3
"""
kt_lint.py — a targeted static checker for Kotlin/Android source, built to catch
the specific classes of "silly stuff that crashes the app" bugs that a normal
IDE/compile pass on a dev machine won't catch, because they only fail at
runtime on-device (or fail on Android's ICU regex engine but not desktop JVM).

This is NOT a full Kotlin parser/compiler. It can't catch everything a real
compile would (type errors, missing imports, etc). What it's good at:

  1. Regex() / Pattern.compile() literals with unescaped { or } — valid on
     desktop JVM regex, but Android's ICU-backed regex engine throws
     PatternSyntaxException on them at runtime. (This is exactly what crashed
     TsplBuilder.)
  2. Unsafe `as Type` casts directly on APIs that are documented to return
     null (getSystemService, findViewById, intent extras, etc). (This is
     exactly what was wrong in UsbPrinter before the fix.)
  3. Mismatched braces/parens/brackets per file (crude but catches truncated
     or malformed files/merge damage).
  4. Duplicate NavHost route strings, and composable("route") destinations
     that reference a screen function name with no matching `fun` anywhere
     in the project.
  5. Merge-conflict markers and TODO/FIXME left in place.
  6. `!!` non-null assertions, listed for manual review (not necessarily bugs,
     but they're where NPEs come from).

Usage:
    python3 kt_lint.py <path-to-project-root>
"""
import re
import sys
import os
from pathlib import Path

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def strip_opaque_regions(text: str):
    """
    Walk the file once, replacing the *contents* of comments, char literals,
    and string literals (normal + raw triple-quoted) with spaces of the same
    length (so brace/paren counting and line/col numbers stay accurate), but
    leave everything else untouched. This is a heuristic tokenizer, not a
    real Kotlin lexer — it's good enough for structural checks.
    """
    out = []
    i, n = 0, len(text)
    while i < n:
        c = text[i]
        # line comment
        if c == '/' and i + 1 < n and text[i+1] == '/':
            j = text.find('\n', i)
            j = n if j == -1 else j
            out.append(text[i:j])
            i = j
            continue
        # block comment
        if c == '/' and i + 1 < n and text[i+1] == '*':
            j = text.find('*/', i + 2)
            j = n if j == -1 else j + 2
            chunk = text[i:j]
            out.append(re.sub(r'[^\n]', ' ', chunk))
            i = j
            continue
        # triple-quoted raw string
        if text.startswith('"""', i):
            j = text.find('"""', i + 3)
            j = n if j == -1 else j + 3
            chunk = text[i:j]
            out.append(re.sub(r'[^\n]', ' ', chunk))
            i = j
            continue
        # normal string literal
        if c == '"':
            j = i + 1
            while j < n and text[j] != '"':
                if text[j] == '\\':
                    j += 2
                else:
                    j += 1
            j = min(j + 1, n)
            chunk = text[i:j]
            out.append(re.sub(r'[^\n]', ' ', chunk))
            i = j
            continue
        # char literal
        if c == "'":
            j = i + 1
            while j < n and text[j] != "'":
                if text[j] == '\\':
                    j += 2
                else:
                    j += 1
            j = min(j + 1, n)
            chunk = text[i:j]
            out.append(re.sub(r'[^\n]', ' ', chunk))
            i = j
            continue
        out.append(c)
        i += 1
    return ''.join(out)


def line_of(text: str, pos: int) -> int:
    return text.count('\n', 0, pos) + 1


# ---------------------------------------------------------------------------
# Check 1: Regex literals with unescaped braces (the ICU-vs-JVM bug class)
# ---------------------------------------------------------------------------

REGEX_CALL_RE = re.compile(
    r'(?:Regex|Pattern\.compile)\s*\(\s*"""(.*?)"""',
    re.DOTALL,
)
REGEX_CALL_RE_PLAIN = re.compile(
    r'(?:Regex|Pattern\.compile)\s*\(\s*"((?:[^"\\]|\\.)*)"',
)

def check_regex_braces(path, raw_text, findings):
    for m in REGEX_CALL_RE.finditer(raw_text):
        pattern = m.group(1)
        _check_one_pattern(path, raw_text, m.start(1), pattern, findings, raw=True)
    for m in REGEX_CALL_RE_PLAIN.finditer(raw_text):
        # skip if this match is actually inside a triple-quoted one (avoid dup)
        pattern = m.group(1)
        # unescape normal Kotlin string escapes
        unescaped = pattern.encode().decode('unicode_escape', errors='ignore')
        _check_one_pattern(path, raw_text, m.start(1), unescaped, findings, raw=False)


def _check_one_pattern(path, raw_text, pos, pattern, findings, raw):
    i, n = 0, len(pattern)
    issues = []
    while i < n:
        c = pattern[i]
        if c == '\\':
            i += 2
            continue
        if c == '{':
            mq = re.match(r'\{\d+(,\d*)?\}', pattern[i:])
            if mq:
                i += mq.end()
                continue
            issues.append(('{', i))
            i += 1
            continue
        if c == '}':
            issues.append(('}', i))
            i += 1
            continue
        i += 1
    if issues:
        ln = line_of(raw_text, pos)
        chars = ', '.join(f"'{ch}' at pattern-offset {off}" for ch, off in issues)
        findings.append({
            'severity': 'HIGH',
            'file': path,
            'line': ln,
            'rule': 'unescaped-regex-brace',
            'msg': (f"Regex literal has unescaped {chars}. Valid on desktop JVM regex, "
                    f"but Android's ICU regex engine throws PatternSyntaxException on this "
                    f"at runtime (this is exactly what crashed TsplBuilder). "
                    f"Pattern seen: {pattern!r}. Escape as \\{{ and \\}}."),
        })


# ---------------------------------------------------------------------------
# Check 2: unsafe `as Type` casts on known-nullable-returning APIs
# ---------------------------------------------------------------------------

NULLABLE_APIS = [
    'getSystemService', 'findViewById', 'getParcelableExtra', 'getSerializableExtra',
    'getStringExtra', 'getIntent().getParcelableExtra', 'bundle.get', 'savedInstanceState.get',
    '.get(', 'openDevice',
]

UNSAFE_CAST_RE = re.compile(r'\b(as)\s+([A-Za-z_][A-Za-z0-9_.]*)\s*(?!\?)')

def check_unsafe_casts(path, raw_text, stripped_text, findings):
    for m in UNSAFE_CAST_RE.finditer(stripped_text):
        # 'as?' would have a '?' right after the type in source; re.match above
        # already looks past whitespace so double check the literal source char.
        end = m.end(2)
        if end < len(raw_text) and raw_text[end] == '?':
            continue  # it's actually `as Type?`, a nullable-typed safe-ish cast
        line_start = raw_text.rfind('\n', 0, m.start()) + 1
        line_text = raw_text[line_start: raw_text.find('\n', m.start())]
        if any(api in line_text for api in NULLABLE_APIS):
            ln = line_of(raw_text, m.start())
            findings.append({
                'severity': 'HIGH',
                'file': path,
                'line': ln,
                'rule': 'unsafe-cast-on-nullable-api',
                'msg': (f"Unsafe `as {m.group(2)}` cast on a call that can return null on "
                        f"some devices/configurations. If it returns null this throws NPE "
                        f"immediately (this is exactly what crashed UsbPrinter). "
                        f"Line: {line_text.strip()!r}. Use `as? {m.group(2)}` and handle null."),
            })


# ---------------------------------------------------------------------------
# Check 3: brace/paren/bracket balance per file
# ---------------------------------------------------------------------------

PAIRS = {'{': '}', '(': ')', '[': ']'}
CLOSERS = {v: k for k, v in PAIRS.items()}

def check_balance(path, raw_text, stripped_text, findings):
    stack = []
    for idx, c in enumerate(stripped_text):
        if c in PAIRS:
            stack.append((c, idx))
        elif c in CLOSERS:
            if not stack or stack[-1][0] != CLOSERS[c]:
                ln = line_of(raw_text, idx)
                findings.append({
                    'severity': 'HIGH',
                    'file': path,
                    'line': ln,
                    'rule': 'brace-mismatch',
                    'msg': f"Unexpected '{c}' — doesn't match the currently open bracket. "
                           f"Possible truncated file or merge damage.",
                })
                return
            stack.pop()
    if stack:
        ch, idx = stack[-1]
        ln = line_of(raw_text, idx)
        findings.append({
            'severity': 'HIGH',
            'file': path,
            'line': ln,
            'rule': 'brace-mismatch',
            'msg': f"Unclosed '{ch}' opened here — never closed by end of file. "
                   f"Possible truncated file or merge damage.",
        })


# ---------------------------------------------------------------------------
# Check 4: NavHost route duplicates / dangling screen references
# ---------------------------------------------------------------------------

COMPOSABLE_ROUTE_RE = re.compile(r'composable\(\s*"([^"]+)"\s*\)\s*\{')
FUN_DEF_RE = re.compile(r'\bfun\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(')
SCREEN_CALL_RE = re.compile(r'\b([A-Z][A-Za-z0-9_]*Screen)\s*\(')

def check_nav_routes(all_files_text, findings):
    seen_routes = {}
    all_fun_names = set()
    for path, (raw, stripped) in all_files_text.items():
        for m in FUN_DEF_RE.finditer(stripped):
            all_fun_names.add(m.group(1))

    for path, (raw, stripped) in all_files_text.items():
        for m in COMPOSABLE_ROUTE_RE.finditer(stripped):
            route = m.group(1)
            ln = line_of(raw, m.start())
            if route in seen_routes:
                findings.append({
                    'severity': 'MEDIUM',
                    'file': path,
                    'line': ln,
                    'rule': 'duplicate-nav-route',
                    'msg': f"Route \"{route}\" is also registered at "
                           f"{seen_routes[route][0]}:{seen_routes[route][1]} — "
                           f"only the first registration will ever be reachable.",
                })
            else:
                seen_routes[route] = (path, ln)

        # look for *Screen(...) calls in this composable block region and make
        # sure the referenced function actually exists somewhere in the project
        for m in SCREEN_CALL_RE.finditer(stripped):
            name = m.group(1)
            if name not in all_fun_names:
                ln = line_of(raw, m.start())
                findings.append({
                    'severity': 'HIGH',
                    'file': path,
                    'line': ln,
                    'rule': 'dangling-screen-reference',
                    'msg': f"Calls `{name}(...)` but no `fun {name}(` is defined anywhere "
                           f"in the project. This will crash (or fail to compile).",
                })


# ---------------------------------------------------------------------------
# Check 5: merge markers / TODO-FIXME
# ---------------------------------------------------------------------------

MERGE_MARKER_RE = re.compile(r'^(<{7}|={7}|>{7})', re.MULTILINE)
TODO_RE = re.compile(r'//\s*(TODO|FIXME)\b.*', re.IGNORECASE)

def check_markers(path, raw_text, findings):
    for m in MERGE_MARKER_RE.finditer(raw_text):
        ln = line_of(raw_text, m.start())
        findings.append({
            'severity': 'HIGH',
            'file': path,
            'line': ln,
            'rule': 'merge-conflict-marker',
            'msg': "Unresolved merge-conflict marker left in the file.",
        })
    for m in TODO_RE.finditer(raw_text):
        ln = line_of(raw_text, m.start())
        findings.append({
            'severity': 'LOW',
            'file': path,
            'line': ln,
            'rule': 'todo-fixme',
            'msg': m.group(0).strip(),
        })


# ---------------------------------------------------------------------------
# Check 6: bare !! (informational)
# ---------------------------------------------------------------------------

BANG_BANG_RE = re.compile(r'!!(?!=)')

def check_bang_bang(path, raw_text, stripped_text, findings):
    for m in BANG_BANG_RE.finditer(stripped_text):
        ln = line_of(raw_text, m.start())
        line_start = raw_text.rfind('\n', 0, m.start()) + 1
        line_end = raw_text.find('\n', m.start())
        line_text = raw_text[line_start:line_end if line_end != -1 else len(raw_text)]
        findings.append({
            'severity': 'LOW',
            'file': path,
            'line': ln,
            'rule': 'non-null-assertion',
            'msg': f"`!!` used — throws NPE with no useful message if this is ever null: "
                   f"{line_text.strip()!r}",
        })


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    root = Path(sys.argv[1] if len(sys.argv) > 1 else '.')
    kt_files = sorted(root.rglob('*.kt'))
    if not kt_files:
        print(f"No .kt files found under {root}")
        return 1

    findings = []
    all_files_text = {}

    for path in kt_files:
        rel = str(path.relative_to(root))
        raw = path.read_text(encoding='utf-8', errors='replace')
        stripped = strip_opaque_regions(raw)
        all_files_text[rel] = (raw, stripped)

        check_regex_braces(rel, raw, findings)
        check_unsafe_casts(rel, raw, stripped, findings)
        check_balance(rel, raw, stripped, findings)
        check_markers(rel, raw, findings)
        check_bang_bang(rel, raw, stripped, findings)

    check_nav_routes(all_files_text, findings)

    sev_order = {'HIGH': 0, 'MEDIUM': 1, 'LOW': 2}
    findings.sort(key=lambda f: (sev_order[f['severity']], f['file'], f['line']))

    counts = {'HIGH': 0, 'MEDIUM': 0, 'LOW': 0}
    for f in findings:
        counts[f['severity']] += 1

    print(f"Scanned {len(kt_files)} Kotlin files under {root}\n")
    print(f"HIGH: {counts['HIGH']}   MEDIUM: {counts['MEDIUM']}   LOW: {counts['LOW']}\n")
    print("=" * 100)

    for f in findings:
        print(f"[{f['severity']:6}] {f['file']}:{f['line']}  ({f['rule']})")
        print(f"          {f['msg']}")
        print()

    return 0 if counts['HIGH'] == 0 else 2


if __name__ == '__main__':
    sys.exit(main())
