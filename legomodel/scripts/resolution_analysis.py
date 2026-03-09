#!/usr/bin/env python3
"""Analyze direct-strategy color accuracy across resolutions for the cat model."""

# Expected correct palette codes for the cat model (from uvlab baseline)
CORRECT = {15, 31, 151, 503}

# Color code -> name mapping (from output)
NAMES = {
    15: "White", 151: "Very Light Bluish Gray", 31: "Lavender", 503: "Very Light Gray",
    20: "Light Lime", 212: "Bright Light Blue", 378: "Sand Green", 77: "Dark Pink",
    323: "Light Aqua", 73: "Medium Blue", 16: "Pink", 17: "Light Green",
    118: "Light Aqua (alt)", 112: "Medium Violet", 100: "Light Salmon",
    71: "Light Bluish Gray", 7: "Light Gray (old)", 72: "Dark Bluish Gray", 0: "Black",
}

data = {
    50:  {15:739, 151:251, 31:60, 503:49, 20:29, 16:19, 212:11, 77:4, 17:2, 118:2, 73:1, 378:1},
    150: {15:8096, 151:1614, 31:462, 503:379, 20:324, 212:133, 16:82, 378:65, 77:52, 323:48, 73:40, 17:22, 118:9, 112:1},
    300: {15:34722, 151:5594, 31:1614, 503:1512, 20:1435, 212:556, 378:302, 77:240, 73:218, 16:180, 323:152, 17:124, 118:28, 100:17, 112:9, 71:5, 7:2, 0:2, 72:1},
    500: {15:98689, 151:14335, 20:4270, 31:4116, 503:4103, 212:1648, 378:969, 73:701, 77:664, 323:380, 16:340, 17:337, 100:67, 112:52, 118:39, 71:19, 7:13, 72:10, 0:1},
}

print(f"{'Res':>5s}  {'Bricks':>7s}  {'Unique':>6s}  {'Correct':>8s}  {'Wrong':>7s}  {'% Correct':>10s}  Wrong colors")
print("-" * 100)

for res in sorted(data.keys()):
    counts = data[res]
    total = sum(counts.values())
    correct_count = sum(v for k, v in counts.items() if k in CORRECT)
    wrong_count = total - correct_count
    pct = 100 * correct_count / total
    wrong_details = sorted(
        [(v, NAMES.get(k, f"#{k}")) for k, v in counts.items() if k not in CORRECT],
        key=lambda x: -x[0]
    )
    wrong_str = ", ".join(f"{n}({c})" for c, n in wrong_details[:5])
    print(f"{res:>5d}  {total:>7d}  {len(counts):>6d}  {correct_count:>8d}  {wrong_count:>7d}  {pct:>9.1f}%  {wrong_str}")
