#!/usr/bin/env python3
"""Extract texture dimensions from GLB files."""
import struct, json, sys

def read_glb_texture_size(path):
    with open(path, 'rb') as f:
        magic, version, length = struct.unpack('<III', f.read(12))
        chunk_len, chunk_type = struct.unpack('<II', f.read(8))
        json_data = json.loads(f.read(chunk_len))
        chunk_len2, chunk_type2 = struct.unpack('<II', f.read(8))
        bin_data = f.read(chunk_len2)

    images = json_data.get('images', [])
    print(f"  Images: {len(images)}")

    for i, img in enumerate(images):
        if 'bufferView' in img:
            bv = json_data['bufferViews'][img['bufferView']]
            offset = bv.get('byteOffset', 0)
            blen = bv['byteLength']
            mime = img.get('mimeType', '?')
            img_bytes = bin_data[offset:offset + blen]
            w, h = 0, 0
            if mime == 'image/png' and len(img_bytes) > 24:
                w = struct.unpack('>I', img_bytes[16:20])[0]
                h = struct.unpack('>I', img_bytes[20:24])[0]
            elif mime == 'image/jpeg' and len(img_bytes) > 2:
                j = 2
                while j < len(img_bytes) - 9:
                    if img_bytes[j] != 0xFF:
                        break
                    marker = img_bytes[j + 1]
                    if marker == 0xD9:
                        break
                    if marker in (0xC0, 0xC2):
                        h = struct.unpack('>H', img_bytes[j+5:j+7])[0]
                        w = struct.unpack('>H', img_bytes[j+7:j+9])[0]
                        break
                    seg_len = struct.unpack('>H', img_bytes[j+2:j+4])[0]
                    j += 2 + seg_len
            if w > 0:
                print(f"  Image {i}: {w}x{h} ({mime})")
            else:
                print(f"  Image {i}: unknown dims ({mime}, {blen} bytes)")

for model in ['the_cats_body.glb', 'labrador_dog.glb', 'pony_cartoon.glb']:
    path = f"models/{model}"
    print(f"{model}:")
    try:
        read_glb_texture_size(path)
    except Exception as e:
        print(f"  Error: {e}")
