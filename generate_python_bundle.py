import os
import tarfile

def main():
    source_dir = "python_bundle"
    output_filename = "python.tar.gz"

    if not os.path.exists(source_dir):
        print(f"Error: Source directory '{source_dir}' not found.")
        print("Please ensure you have the python environment in the 'python_bundle' folder.")
        return

    print(f"Compressing '{source_dir}' into '{output_filename}'...")
    
    with tarfile.open(output_filename, "w:gz") as tar:
        tar.add(source_dir, arcname=".")

    print("\n" + "="*40)
    print("SUCCESS: Python environment bundled!")
    print(f"ACTION REQUIRED: Move '{output_filename}' to the 'assets/' folder.")
    print("Example: mv python.tar.gz assets/")
    print("="*40)

if __name__ == "__main__":
    main()