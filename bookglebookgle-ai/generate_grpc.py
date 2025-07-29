#!/usr/bin/env python3
"""
Script to generate gRPC Python stubs from protobuf definitions
"""

import subprocess
import sys
from pathlib import Path


def generate_grpc_stubs():
    """Generate Python gRPC stubs from protobuf files"""
    
    # Paths
    proto_dir = Path("protos")
    output_dir = Path("src/grpc_server/generated")
    
    # Create output directory
    output_dir.mkdir(parents=True, exist_ok=True)
    
    # Create __init__.py in generated directory
    (output_dir / "__init__.py").touch()
    
    # Find all .proto files
    proto_files = list(proto_dir.glob("*.proto"))
    
    if not proto_files:
        print("No .proto files found in the protos directory")
        return False
    
    print(f"Found {len(proto_files)} proto files:")
    for proto_file in proto_files:
        print(f"  - {proto_file}")
    
    try:
        # Generate Python stubs
        cmd = [
            sys.executable, "-m", "grpc_tools.protoc",
            f"--proto_path={proto_dir}",
            f"--python_out={output_dir}",
            f"--grpc_python_out={output_dir}",
            # "--mypy_out={output_dir}",  # Skip mypy for now
        ]
        
        # Add all proto files
        for proto_file in proto_files:
            cmd.append(str(proto_file))
        
        print("Running command:")
        print(" ".join(cmd))
        
        result = subprocess.run(cmd, check=True, capture_output=True, text=True)
        
        print("Successfully generated gRPC stubs")
        
        # List generated files
        generated_files = list(output_dir.glob("*"))
        print(f"Generated {len(generated_files)} files:")
        for file in generated_files:
            print(f"  - {file.name}")
        
        # Fix imports in generated files (if needed)
        fix_generated_imports(output_dir)
        
        return True
        
    except subprocess.CalledProcessError as e:
        print(f"Error generating gRPC stubs: {e}")
        print("STDOUT:", e.stdout)
        print("STDERR:", e.stderr)
        return False
    except Exception as e:
        print(f"Unexpected error: {e}")
        return False


def fix_generated_imports(output_dir: Path):
    """Fix imports in generated files for better Python compatibility"""
    
    try:
        grpc_py_files = list(output_dir.glob("*_pb2_grpc.py"))
        
        for grpc_file in grpc_py_files:
            content = grpc_file.read_text()
            
            # Find the corresponding _pb2 file name
            pb2_file_name = grpc_file.name.replace("_grpc.py", ".py")
            pb2_module_name = pb2_file_name.replace(".py", "")

            # Construct the problematic import statement
            problematic_import = f"import {pb2_module_name} as "
            
            # Replace with a relative import
            if problematic_import in content:
                content = content.replace(
                    problematic_import,
                    f"from . import {pb2_module_name} as "
                )
                grpc_file.write_text(content)
                print(f"Fixed import in {grpc_file.name}")

        print("Import fixes applied.")
        
    except Exception as e:
        print(f"Warning: Could not fix imports: {e}")


if __name__ == "__main__":
    print("Generating gRPC stubs for BGBG AI Service")
    print("=" * 50)
    
    success = generate_grpc_stubs()
    
    if success:
        print("\ngRPC stub generation completed successfully!")
        print("\nNext steps:")
        print("1. Update imports in your servicer files")
        print("2. Test the gRPC server")
        print("3. Implement the actual service methods")
    else:
        print("\ngRPC stub generation failed!")
        print("Please check the error messages above and ensure:")
        print("1. grpcio-tools is installed: pip install grpcio-tools")
        print("2. Proto files are valid")
        print("3. All dependencies are available")
        
        sys.exit(1)