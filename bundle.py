import os
import tarfile

def main():
    source_dir = "python_bundle"
    output_dir = "assets"
    output_filename = "python.tar.gz"
    output_path = os.path.join(output_dir, output_filename)

    if not os.path.exists(source_dir):
        print(f"Error: Source directory '{source_dir}' not found.")
        return

    if not os.path.exists(output_dir):
        os.makedirs(output_dir)
        print(f"Created directory: {output_dir}")

    print(f"Compressing '{source_dir}' into '{output_path}'...")
    
    with tarfile.open(output_path, "w:gz") as tar:
        # arcname="." ensures that we don't have a top-level "python_bundle" folder inside the tar
        tar.add(source_dir, arcname=".")

    print("Successfully bundled Python environment!")

if __name__ == "__main__":
    main()
