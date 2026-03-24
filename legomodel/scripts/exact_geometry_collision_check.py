#!/usr/bin/env python3
"""Mesh-based collision checker for LDraw models.

This script resolves real LDraw part geometry (.dat), voxelizes occupied volume,
and reports overlapping placement pairs in an exported .ldr model.

Notes:
- Uses local Studio/LDraw parts library (default: /Applications/Studio 2.0/ldraw)
- Uses a mesh ray-cast solid test (parity) at configurable sample step (LDU)
- Useful for finding geometry overlaps missed by coarse stud/plate masks
"""

from __future__ import annotations

import argparse
import math
import os
from dataclasses import dataclass
from functools import lru_cache
from typing import Dict, Iterable, List, Optional, Tuple

EPS = 1e-9
SLOPE_PARTS = {
    "3037.dat",
    "3039.dat",
    "3040b.dat",
    "3298.dat",
    "4286.dat",
    "85984.dat",
}


@dataclass(frozen=True)
class Placement:
    line_no: int
    part: str
    transform: Tuple[float, ...]  # 3x4 affine row-major


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Exact-geometry collision check for .ldr")
    parser.add_argument("ldr_path", help="Path to .ldr model")
    parser.add_argument(
        "--parts-dir",
        default="/Applications/Studio 2.0/ldraw",
        help="LDraw base directory (contains parts/, p/, UnOfficial/)"
    )
    parser.add_argument(
        "--step",
        type=float,
        default=2.0,
        help="Voxel sampling step in LDU (default: 2.0). Smaller = more exact, slower."
    )
    parser.add_argument(
        "--mode",
        choices=["slope", "all"],
        default="slope",
        help="Report slope-involved pairs only (default) or all pairs"
    )
    parser.add_argument(
        "--max-pairs",
        type=int,
        default=40,
        help="Max pair rows to print (default: 40)"
    )
    return parser.parse_args()


def as_affine(m3x3: Iterable[float], txyz: Iterable[float]) -> Tuple[float, ...]:
    m = list(m3x3)
    t = list(txyz)
    return (
        m[0], m[1], m[2], t[0],
        m[3], m[4], m[5], t[1],
        m[6], m[7], m[8], t[2],
    )


def apply_affine(m: Tuple[float, ...], v: Tuple[float, float, float]) -> Tuple[float, float, float]:
    x, y, z = v
    return (
        m[0] * x + m[1] * y + m[2] * z + m[3],
        m[4] * x + m[5] * y + m[6] * z + m[7],
        m[8] * x + m[9] * y + m[10] * z + m[11],
    )


def mul_affine(a: Tuple[float, ...], b: Tuple[float, ...]) -> Tuple[float, ...]:
    return (
        a[0] * b[0] + a[1] * b[4] + a[2] * b[8],
        a[0] * b[1] + a[1] * b[5] + a[2] * b[9],
        a[0] * b[2] + a[1] * b[6] + a[2] * b[10],
        a[0] * b[3] + a[1] * b[7] + a[2] * b[11] + a[3],
        a[4] * b[0] + a[5] * b[4] + a[6] * b[8],
        a[4] * b[1] + a[5] * b[5] + a[6] * b[9],
        a[4] * b[2] + a[5] * b[6] + a[6] * b[10],
        a[4] * b[3] + a[5] * b[7] + a[6] * b[11] + a[7],
        a[8] * b[0] + a[9] * b[4] + a[10] * b[8],
        a[8] * b[1] + a[9] * b[5] + a[10] * b[9],
        a[8] * b[2] + a[9] * b[6] + a[10] * b[10],
        a[8] * b[3] + a[9] * b[7] + a[10] * b[11] + a[11],
    )


class GeometryResolver:
    def __init__(self, base_dir: str, step: float):
        self.base_dir = base_dir
        self.step = step

    def _candidate_relpaths(self, name: str) -> List[str]:
        norm = name.replace("\\", "/").strip().lower()
        if norm.startswith("s/"):
            return [f"parts/{norm}", f"UnOfficial/parts/{norm}"]
        if norm.startswith("48/") or norm.startswith("8/"):
            return [f"p/{norm}", f"UnOfficial/p/{norm}"]
        if "/" not in norm:
            return [
                f"parts/{norm}",
                f"UnOfficial/parts/{norm}",
                f"p/{norm}",
                f"UnOfficial/p/{norm}",
            ]
        return [norm]

    def find_part_file(self, name: str) -> Optional[str]:
        for rel in self._candidate_relpaths(name):
            path = os.path.join(self.base_dir, rel)
            if os.path.isfile(path):
                return path
        return None

    @lru_cache(maxsize=None)
    def load_triangles(self, part_name: str) -> Tuple[Tuple[Tuple[float, float, float], ...], ...]:
        path = self.find_part_file(part_name)
        if not path:
            return tuple()

        triangles: List[Tuple[Tuple[float, float, float], ...]] = []

        def recurse(file_path: str, transform: Tuple[float, ...]) -> None:
            with open(file_path, "r", errors="ignore") as fh:
                for line in fh:
                    toks = line.strip().split()
                    if not toks:
                        continue

                    t = toks[0]
                    if t == "1" and len(toks) >= 15:
                        try:
                            x, y, z = map(float, toks[2:5])
                            m = list(map(float, toks[5:14]))
                            sub = toks[14]
                        except ValueError:
                            continue

                        sub_path = self.find_part_file(sub)
                        if not sub_path:
                            continue

                        sub_affine = as_affine(m, (x, y, z))
                        recurse(sub_path, mul_affine(transform, sub_affine))

                    elif t == "3" and len(toks) >= 11:
                        try:
                            vals = list(map(float, toks[2:11]))
                        except ValueError:
                            continue
                        v1 = apply_affine(transform, (vals[0], vals[1], vals[2]))
                        v2 = apply_affine(transform, (vals[3], vals[4], vals[5]))
                        v3 = apply_affine(transform, (vals[6], vals[7], vals[8]))
                        triangles.append((v1, v2, v3))

                    elif t == "4" and len(toks) >= 14:
                        try:
                            vals = list(map(float, toks[2:14]))
                        except ValueError:
                            continue
                        v1 = apply_affine(transform, (vals[0], vals[1], vals[2]))
                        v2 = apply_affine(transform, (vals[3], vals[4], vals[5]))
                        v3 = apply_affine(transform, (vals[6], vals[7], vals[8]))
                        v4 = apply_affine(transform, (vals[9], vals[10], vals[11]))
                        triangles.append((v1, v2, v3))
                        triangles.append((v1, v3, v4))

        recurse(path, as_affine((1.0, 0.0, 0.0,
                                 0.0, 1.0, 0.0,
                                 0.0, 0.0, 1.0), (0.0, 0.0, 0.0)))
        return tuple(triangles)

    @staticmethod
    def _ray_hits_triangle(px: float, py: float, pz: float,
                           tri: Tuple[Tuple[float, float, float], ...]) -> bool:
        (ax, ay, az), (bx, by, bz), (cx, cy, cz) = tri

        e1x, e1y, e1z = bx - ax, by - ay, bz - az
        e2x, e2y, e2z = cx - ax, cy - ay, cz - az

        # Ray dir = +X
        hx, hy, hz = 0.0, -e2z, e2y
        a = e1x * hx + e1y * hy + e1z * hz
        if abs(a) < EPS:
            return False

        f = 1.0 / a
        sx, sy, sz = px - ax, py - ay, pz - az
        u = f * (sx * hx + sy * hy + sz * hz)
        if u < -EPS or u > 1.0 + EPS:
            return False

        qx = sy * e1z - sz * e1y
        qy = sz * e1x - sx * e1z
        qz = sx * e1y - sy * e1x

        v = f * qx
        if v < -EPS or u + v > 1.0 + EPS:
            return False

        t = f * (e2x * qx + e2y * qy + e2z * qz)
        return t > EPS

    @classmethod
    def _inside_mesh(cls,
                     px: float,
                     py: float,
                     pz: float,
                     tris: Tuple[Tuple[Tuple[float, float, float], ...], ...]) -> bool:
        hits = 0
        for tri in tris:
            if cls._ray_hits_triangle(px, py, pz, tri):
                hits += 1
        return (hits % 2) == 1

    @lru_cache(maxsize=None)
    def occupancy_points(self, part_name: str) -> Tuple[Tuple[float, float, float], ...]:
        tris = self.load_triangles(part_name)
        if not tris:
            return tuple()

        xs = [v[0] for tri in tris for v in tri]
        ys = [v[1] for tri in tris for v in tri]
        zs = [v[2] for tri in tris for v in tri]

        step = self.step
        ox = math.floor(min(xs) / step) * step
        oy = math.floor(min(ys) / step) * step
        oz = math.floor(min(zs) / step) * step

        nx = int(math.ceil((max(xs) - ox) / step))
        ny = int(math.ceil((max(ys) - oy) / step))
        nz = int(math.ceil((max(zs) - oz) / step))

        occupied: List[Tuple[float, float, float]] = []
        for ix in range(nx):
            cx = ox + (ix + 0.5) * step
            for iy in range(ny):
                cy = oy + (iy + 0.5) * step
                for iz in range(nz):
                    cz = oz + (iz + 0.5) * step
                    if self._inside_mesh(cx, cy, cz, tris):
                        occupied.append((cx, cy, cz))

        return tuple(occupied)


def load_placements(ldr_path: str) -> List[Placement]:
    placements: List[Placement] = []
    with open(ldr_path, "r", errors="ignore") as fh:
        for line_no, line in enumerate(fh, 1):
            if not line.startswith("1 "):
                continue
            toks = line.strip().split()
            if len(toks) < 15:
                continue
            try:
                x, y, z = map(float, toks[2:5])
                m = list(map(float, toks[5:14]))
                part = toks[14].lower()
            except ValueError:
                continue

            placements.append(
                Placement(
                    line_no=line_no,
                    part=part,
                    transform=as_affine(m, (x, y, z)),
                )
            )
    return placements


def detect_collisions(placements: List[Placement],
                      resolver: GeometryResolver) -> Tuple[List[Tuple[int, int]], List[Tuple[int, int]]]:
    """Returns (all_pairs, slope_pairs) as placement-index pairs."""
    owner_by_voxel: Dict[Tuple[int, int, int], int] = {}
    all_pairs = set()
    slope_pairs = set()

    step = resolver.step

    for i, placement in enumerate(placements):
        local_points = resolver.occupancy_points(placement.part)
        if not local_points:
            continue

        for local in local_points:
            wx, wy, wz = apply_affine(placement.transform, local)
            key = (
                math.floor((wx + 1e-6) / step),
                math.floor((wy + 1e-6) / step),
                math.floor((wz + 1e-6) / step),
            )
            owner = owner_by_voxel.get(key)
            if owner is None:
                owner_by_voxel[key] = i
                continue

            if owner == i:
                continue

            a, b = sorted((owner, i))
            all_pairs.add((a, b))
            if placements[a].part in SLOPE_PARTS or placements[b].part in SLOPE_PARTS:
                slope_pairs.add((a, b))

    return sorted(all_pairs), sorted(slope_pairs)


def main() -> int:
    args = parse_args()

    if args.step <= 0:
        raise SystemExit("--step must be > 0")

    if not os.path.isfile(args.ldr_path):
        raise SystemExit(f"LDR file not found: {args.ldr_path}")

    if not os.path.isdir(args.parts_dir):
        raise SystemExit(f"LDraw parts dir not found: {args.parts_dir}")

    placements = load_placements(args.ldr_path)
    if not placements:
        print("No type-1 part lines found in model.")
        return 0

    resolver = GeometryResolver(args.parts_dir, float(args.step))
    all_pairs, slope_pairs = detect_collisions(placements, resolver)

    selected = slope_pairs if args.mode == "slope" else all_pairs

    print(f"Model: {args.ldr_path}")
    print(f"Parts dir: {args.parts_dir}")
    print(f"Sample step: {args.step} LDU")
    print(f"Placements: {len(placements)}")
    print(f"Collision pairs (all): {len(all_pairs)}")
    print(f"Collision pairs (slope-involved): {len(slope_pairs)}")
    print(f"Reporting mode: {args.mode} ({len(selected)} pairs)")

    if not selected:
        return 0

    print("\nPairs:")
    limit = max(0, int(args.max_pairs))
    for a, b in selected[:limit]:
        pa = placements[a]
        pb = placements[b]
        print(
            f"  line {pa.line_no} {pa.part} <-> "
            f"line {pb.line_no} {pb.part}"
        )

    if len(selected) > limit:
        print(f"  ... {len(selected) - limit} more")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
