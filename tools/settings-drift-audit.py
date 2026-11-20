#!/usr/bin/env python3
"""设置页「数据层 ↔ 代码」漂移审计。

背景
----
`ui/settings/SettingsHierarchy.kt` 想当设置页的唯一真相源，但迁移是逐屏做的：
已迁移的 5 组由渲染器接管，剩下的 watchface / app_list / animation / system
仍由 `SettingsActivity.kt` 手写。两边各改各的，就会漂：

- 数据层写 `low_res_icons`，代码里行 key 是 `low_res` —— 渲染出来鱼眼、滚动记忆、
  搜索索引全对不上，而且**不会报错**；
- 代码里新加的「天气」「点击动作」整块，数据层根本没有；
- 数据层留着代码里已经删掉的项。

这个脚本把两边摊开比对，先给一张「屏 → 数据覆盖率」表，再列三类问题。

用法
----
    python3 tools/settings-drift-audit.py            # 报告
    python3 tools/settings-drift-audit.py --check    # CI：有 ERROR 就退出 1
    python3 tools/settings-drift-audit.py --screen WATCH_FACE_MORE
"""

import argparse
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "app/src/main/kotlin/com/example/wlauncher")
ACTIVITY = os.path.join(SRC, "SettingsActivity.kt")
HIERARCHY = os.path.join(SRC, "ui/settings/SettingsHierarchy.kt")

# 屏 → 组的人工映射。历史上大量设置按「主题」分组，代码却按「屏」组织，
# 于是 app_list / animation 这类组一度是 1:N。按屏重建成组之后，两边基本 1:1；
# 这张表仍然保留，因为组名与屏名不总是可直接推导（watch_face vs WATCH_FACES）。
# 已整页搬出的屏（DISPLAY_SIZE / APP_LIST_BACKGROUND / MOTION_PROFILE）不再列在这里：
# 它们的行 key 由 HANDWRITTEN 直接排除，映射留着只会变成死数据。
SCREEN_GROUP = {
    "APPEARANCE": "appearance",
    "WATCH_FACES": "watch_face",
    "WATCH_FACE_MORE": "watch_face_more",
    "APP_LIST_BEHAVIOR": "app_list_behavior",
    "PERFORMANCE": "performance",
    "PERFORMANCE_RENDERING": "performance_rendering",
    "PERFORMANCE_MOTION": "performance_motion",
    "PERFORMANCE_SYSTEM": "performance_system",
    "SIDE_SCREEN": "side_screen",
    "NOTIFICATION_SETTINGS": "notifications",
    "GESTURE_SETTINGS": "gestures",
    "FEATURE_TOGGLES": "feature_toggles",
    "TOOLS": "tools",
    "BACKUP": "backup",
    "BACKUP_EXPORT": "backup_export",
    "BACKUP_IMPORT": "backup_import",
}

# 还没有进数据层的屏：整屏没有模型，属「缺组」而非「缺条目」。
# MOTION_PROFILE 不在代码侧扫描范围内（已整页搬到独立文件，见下方 extra）。
UNMODELED_SCREENS = {"AOD", "ICON_PACKS", "HIDDEN_APPS", "DONATE", "DEBUG", "TAP_ACTION_CATEGORIES",
                     "TAP_ACTION_CATEGORY", "MUSIC_TEXT_ANIMATION", "ABOUT", "LANGUAGE"}

# 有意保持手写的页面（整页搬出 SettingsActivity，不数据化）。
# 这些文件的 key 进 code_keys 只是为了查「重名」，不要求进数据层。
HANDWRITTEN = {"DisplaySizeScreen", "AppListBackgroundScreen", "MotionProfileScreen"}

# ROOT 屏不是设置页，是导航菜单，行 key 是入口名，不参与比对。
NAV_SCREEN = "ROOT"

MIGRATED = {"appearance", "app_list_behavior", "watch_face_more", "side_screen",
            "notifications", "gestures", "feature_toggles",
            "performance", "performance_rendering", "performance_motion", "performance_system",
            "tools", "backup", "backup_export", "backup_import"}

# 已交给渲染器的屏（覆盖率表里标 ✔，不再统计行 key）。
MIGRATED_SCREENS = {"APPEARANCE", "APP_LIST_BEHAVIOR", "WATCH_FACE_MORE",
                    "SIDE_SCREEN", "NOTIFICATION_SETTINGS", "GESTURE_SETTINGS",
                    "FEATURE_TOGGLES", "PERFORMANCE", "PERFORMANCE_RENDERING",
                    "PERFORMANCE_MOTION", "PERFORMANCE_SYSTEM",
                    "TOOLS", "BACKUP", "BACKUP_EXPORT", "BACKUP_IMPORT"}


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


def strip_literals(line):
    """去掉字符串字面量与注释，避免括号计数被文案里的 `(` 带偏。"""
    line = re.sub(r'"(?:\\.|[^"\\])*"', '""', line)
    line = re.sub(r"//.*$", "", line)
    return line


def depth_of(line):
    s = strip_literals(line)
    return s.count("(") + s.count("{") + s.count("[") - s.count(")") - s.count("}") - s.count("]")


def parse_screens(src):
    """按 `SettingsDestination.X ->` 切块，括号配平确定块的结尾。"""
    lines = src.split("\n")
    marks = []
    for i, line in enumerate(lines):
        m = re.match(r"^\s*SettingsDestination\.([A-Z_]+) ->(.*)$", line)
        if m:
            marks.append((i, m.group(1), m.group(2)))
    screens = {}
    for i, name, rest in marks:
        depth = depth_of(rest)
        j = i
        while depth > 0 and j + 1 < len(lines):
            j += 1
            depth += depth_of(lines[j])
        block = "\n".join(lines[i:j + 1])
        keys = re.findall(r'^\s*(?:row|item)\("([a-z0-9_]+)"\)', block, re.M)
        screens[name] = keys
    return screens


def camel_to_snake(name):
    return re.sub(r"(?<!^)(?=[A-Z])", "_", name).lower()


def parse_groups(src):
    """切出每个 SettingsGroup，返回 {组名(snake): [条目]}。"""
    marks = [(m.start(), m.group(1)) for m in
             re.finditer(r"private val (\w+) = SettingsGroup\(", src)]
    groups = {}
    for idx, (pos, name) in enumerate(marks):
        end = marks[idx + 1][0] if idx + 1 < len(marks) else len(src)
        body = src[pos:end]
        entries = []
        emarks = [(m.start(), m.group(1)) for m in
                  re.finditer(r"SettingsEntry\.(\w+)\(", body)]
        for eidx, (epos, kind) in enumerate(emarks):
            eend = emarks[eidx + 1][0] if eidx + 1 < len(emarks) else len(body)
            chunk = body[epos:eend]
            eid = re.search(r'\bid = "([a-z0-9_]+)"', chunk)
            if not eid:
                continue
            pref = re.search(r'prefKey = "([A-Za-z0-9_]+)"', chunk)
            entries.append({
                "id": eid.group(1),
                "kind": kind,
                "prefKey": pref.group(1) if pref else None,
                "planned": "EntryStatus.PLANNED" in chunk,
            })
        groups[camel_to_snake(name)] = entries
    return groups


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true", help="有 ERROR 时退出码 1")
    ap.add_argument("--screen", help="只看某个屏")
    args = ap.parse_args()

    screens = parse_screens(read(ACTIVITY))
    groups = parse_groups(read(HIERARCHY))

    # 已迁出到独立文件的屏，行 key 也算代码侧
    extra = {}
    for fname in ("ui/settings/DisplaySizeScreen.kt",
                 "ui/settings/AppListBackgroundScreen.kt",
                 "ui/settings/MotionProfileScreen.kt"):
        p = os.path.join(SRC, fname)
        if os.path.exists(p):
            extra[os.path.splitext(os.path.basename(fname))[0]] = re.findall(
                r'(?:row|item)\("([a-z0-9_]+)"\)', read(p))

    code_keys = {}  # key -> 出现的屏/文件集合
    for screen, keys in screens.items():
        for k in keys:
            code_keys.setdefault(k, set()).add(screen)
    for fname, keys in extra.items():
        for k in keys:
            code_keys.setdefault(k, set()).add(fname)

    data_ids = {}
    for group, entries in groups.items():
        for e in entries:
            data_ids.setdefault(e["id"], set()).add(group)

    errors, warns, infos = [], [], []

    # --- 覆盖率表 -------------------------------------------------------------------
    print("屏 → 数据覆盖率")
    print(f"{'屏':26s} {'行数':>4s} {'命中':>4s}  覆盖者")
    for screen in sorted(screens):
        if screen == NAV_SCREEN:
            continue
        keys = screens[screen]
        if not keys:
            if screen in MIGRATED_SCREENS:
                print(f"{screen:26s} {'—':>4s} {'—':>7s}  ✔ 已由渲染器接管")
            continue
        hit = [k for k in keys if k in data_ids]
        owners = {g for k in hit for g in data_ids[k]}
        rate = f"{len(hit)}/{len(keys)}"
        flag = "" if screen not in UNMODELED_SCREENS else "  ⚠ 未建模"
        print(f"{screen:26s} {len(keys):4d} {rate:>7s}  {','.join(sorted(owners)) or '—'}{flag}")
    for fname, keys in extra.items():
        hit = [k for k in keys if k in data_ids]
        print(f"{fname + '(独立文件)':26s} {len(keys):4d} {f'{len(hit)}/{len(keys)}':>7s}  "
              f"{'手写页 + 数据条目混合' if hit else '纯手写'}")
    print()

    # --- A. 数据层条目在代码里找不到 -------------------------------------------------
    for eid, gset in sorted(data_ids.items()):
        if gset & MIGRATED:
            continue  # 已迁移组：代码已交给渲染器，当然没有行 key
        if eid in code_keys:
            continue
        info = next(e for g in gset for e in groups[g] if e["id"] == eid)
        tag = ",".join(sorted(gset))
        if info["planned"]:
            infos.append(f"[PLANNED] {eid:38s} 组 {tag}")
        else:
            errors.append(f"[幽灵条目] {eid:38s} 组 {tag} —— 数据层有，代码里没这一行")

    # --- B. 代码行 key 在数据层找不到 ------------------------------------------------
    # 未迁移的屏本来就没有数据条目，这类只算 WARN：覆盖率表已经说明了差距，
    # 再在 ERROR 里刷 80 行只会把真正会画错的条目淹掉。
    for key, screen_set in sorted(code_keys.items()):
        if key in data_ids or key == NAV_SCREEN:
            continue
        if screen_set == {NAV_SCREEN}:
            continue  # ROOT 菜单入口，不是设置项
        if screen_set <= HANDWRITTEN:
            continue  # 整页手写的屏，条目本来就不进数据层
        owners = sorted({SCREEN_GROUP[s] for s in screen_set if s in SCREEN_GROUP})
        unmodeled = sorted({s for s in screen_set if s in UNMODELED_SCREENS})
        where = ",".join(sorted(screen_set))
        if owners:
            warns.append(f"[未迁移]   {key:38s} 屏 {where} → 迁到组 {','.join(owners)}")
        elif unmodeled:
            infos.append(f"[未建模]   {key:38s} 屏 {where} —— 该屏还没有数据组")
        else:
            warns.append(f"[无归属]   {key:38s} 屏 {where}")

    # --- C. 只差后缀的 key（几乎肯定是拼写漂移） --------------------------------------
    SUFFIXES = ("_enabled", "_toggle", "_percent", "_icons", "_animation")
    for eid in sorted(data_ids):
        if eid in code_keys or data_ids[eid] & MIGRATED:
            continue
        base = eid
        for s in SUFFIXES:
            base = base.replace(s, "")
        near = [k for k in code_keys if k != eid and k.startswith(base) and len(k) - len(base) <= 10]
        if near and eid not in [e.split()[-1] for e in errors]:
            warns.append(f"[疑似改名] {eid:38s} ≈ 代码里的 {', '.join(sorted(near))}")

    def dump(title, items, filter_by=None):
        if filter_by:
            items = [x for x in items if filter_by in x]
        if not items:
            return
        print(f"{title}（{len(items)}）")
        for i in items:
            print("  " + i)
        print()

    dump("ERROR", errors, args.screen)
    dump("WARN", warns, args.screen)
    dump("INFO", infos, args.screen)

    print(f"合计 ERROR {len(errors)} / WARN {len(warns)} / INFO {len(infos)}")
    if args.check and errors:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
