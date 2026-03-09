#!/usr/bin/env python3
"""Analyze direct-strategy color accuracy across resolutions for the labrador."""

CORRECT = {84, 178, 28, 86}

NAMES = {
    84: "Medium Nougat", 178: "Flat Dark Gold", 28: "Dark Tan", 86: "Light Brown",
    6: "Brown", 8: "Dark Bluish Gray", 320: "Dark Red", 70: "Reddish Brown",
    0: "Black", 7: "Light Gray", 72: "Dark Bluish Gray", 326: "Olive Green",
    71: "Light Bluish Gray", 16: "Pink", 19: "Tan", 308: "Dark Brown",
    26: "Magenta", 335: "Sand Red", 323: "Light Aqua", 373: "Sand Purple",
    503: "Very Light Gray", 151: "V.Lt Bluish Gray", 78: "Medium Nougat(alt)",
    5: "Red", 378: "Sand Green", 92: "Flesh", 69: "Dark Tan(alt)",
}

data = {
    50: {84:588, 178:150, 86:85, 28:73, 8:25, 6:22, 16:12, 70:9, 320:8, 7:4, 0:2, 72:1, 308:1},
    150: {84:5422, 178:1840, 86:684, 28:476, 6:424, 8:289, 70:133, 320:131, 16:86, 7:75, 0:59, 72:32, 326:19, 71:15, 19:9, 308:5, 26:3, 335:2, 323:2, 373:1},
    300: {84:21859, 178:7695, 86:2759, 28:1782, 6:1953, 8:1183, 70:664, 320:566, 0:313, 7:306, 16:211, 72:162, 326:112, 71:63, 19:62, 308:55, 323:33, 335:18, 26:18, 503:9, 373:8, 78:1, 5:1, 378:1, 151:1},
    500: {84:61073, 178:22132, 86:7762, 28:4819, 6:5547, 8:3309, 70:1963, 320:1640, 0:1076, 7:921, 72:444, 16:412, 326:404, 19:236, 71:188, 308:178, 323:110, 26:84, 335:54, 373:38, 503:30, 151:20, 78:8, 92:3, 378:2, 69:1, 5:1},
}

print(f"{'Res':>5s}  {'Bricks':>7s}  {'Unique':>6s}  {'Correct':>8s}  {'Wrong':>7s}  {'%Correct':>9s}  {'%Wrong':>7s}  Top wrong colors")
print("-" * 115)

for res in sorted(data.keys()):
    counts = data[res]
    total = sum(counts.values())
    correct_count = sum(v for k, v in counts.items() if k in CORRECT)
    wrong_count = total - correct_count
    pct_correct = 100 * correct_count / total
    pct_wrong = 100 * wrong_count / total
    
    # Separate truly wrong-hue from dark-but-same-family
    dark_family = {6, 70, 308}  # Brown, Reddish Brown, Dark Brown - warm family
    neutral = {0, 7, 8, 71, 72}  # grays/black
    truly_wrong = set(counts.keys()) - CORRECT - dark_family - neutral - {16}  # Pink is borderline
    
    truly_wrong_count = sum(counts.get(k, 0) for k in truly_wrong)
    pct_truly_wrong = 100 * truly_wrong_count / total
    
    wrong_details = sorted(
        [(v, NAMES.get(k, f"#{k}")) for k, v in counts.items() if k not in CORRECT],
        key=lambda x: -x[0]
    )
    wrong_str = ", ".join(f"{n}({c})" for c, n in wrong_details[:5])
    print(f"{res:>5d}  {total:>7d}  {len(counts):>6d}  {correct_count:>8d}  {wrong_count:>7d}  {pct_correct:>8.1f}%  {pct_wrong:>6.1f}%  {wrong_str}")

print()
print("Truly wrong-hue breakdown (Olive Green, Sand Purple, Magenta, Light Aqua, Sand Red, etc.):")
print(f"{'Res':>5s}  {'Total':>7s}  {'Truly wrong':>12s}  {'% Truly wrong':>14s}")
print("-" * 45)
for res in sorted(data.keys()):
    counts = data[res]
    total = sum(counts.values())
    truly_wrong_codes = {326, 323, 26, 335, 373, 503, 151, 378, 5, 92, 78, 69, 19}
    tw = sum(counts.get(k, 0) for k in truly_wrong_codes)
    print(f"{res:>5d}  {total:>7d}  {tw:>12d}  {100*tw/total:>13.2f}%")
