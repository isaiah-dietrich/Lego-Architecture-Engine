from pathlib import Path

from PIL import Image


def export_pixels(image_path: Path, output_csv: Path) -> None:
    image = Image.open(image_path).convert("RGB")
    width, height = image.size
    pixels = image.load()

    with output_csv.open("w", encoding="utf-8") as file:
        file.write(f"source_image,{image_path}\n")
        file.write(f"width,{width},height,{height}\n")
        file.write("x,y,r,g,b,hex\n")
        for y in range(height):
            rows = []
            for x in range(width):
                red, green, blue = pixels[x, y]
                rows.append(f"{x},{y},{red},{green},{blue},#{red:02X}{green:02X}{blue:02X}\n")
            file.writelines(rows)

    print(f"wrote {output_csv} ({width}x{height})")


def main() -> None:
    cache_dir = Path.home() / "Library/Application Support/Code/User/workspaceStorage/vscode-chat-images"
    output_dir = Path("/Users/isaiahdietrich/Desktop/Projects/Lego-Architecture-Engine/legomodel/output/analysis")
    output_dir.mkdir(parents=True, exist_ok=True)

    export_pairs = [
        (cache_dir / "image-1772926773775.png", output_dir / "the_cats_body_glb_pixels.csv"),
        (cache_dir / "image-1772926832348.png", output_dir / "the_cats_body_ldraw_pixels.csv"),
    ]

    for image_path, output_csv in export_pairs:
        export_pixels(image_path, output_csv)


if __name__ == "__main__":
    main()
