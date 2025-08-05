#!/usr/bin/env python3
"""
EC2 호환 gRPC 코드 생성 스크립트

EC2 서버와 동일한 protobuf 버전(5.x)을 사용하여 gRPC 코드를 생성합니다.
"""

import os
import sys
import subprocess
import tempfile
from pathlib import Path

def generate_grpc_for_ec2():
    print("Starting gRPC code generation for EC2 compatibility...")
    
    project_root = Path(".").resolve()
    proto_file = project_root / "protos" / "ai_service.proto"
    output_dir = project_root / "src" / "grpc_server" / "generated"
    
    # 임시 환경에서 protobuf 5.x로 코드 생성
    with tempfile.TemporaryDirectory() as temp_dir:
        temp_env = os.environ.copy()
        
        print("Installing protobuf 4.x...")
        install_cmd = [
            sys.executable, "-m", "pip", "install", 
            "protobuf>=4.0.0,<5.0.0", 
            "grpcio-tools<1.61.0",
            "--target", temp_dir
        ]
        
        result = subprocess.run(install_cmd, capture_output=True, text=True, encoding='utf-8')
        if result.returncode != 0:
            print(f"Protobuf installation failed: {result.stderr}")
            return False
        
        # PYTHONPATH에 임시 디렉터리 추가
        temp_env["PYTHONPATH"] = f"{temp_dir}{os.pathsep}{temp_env.get('PYTHONPATH', '')}"
        
        print("Generating gRPC code...")
        generate_cmd = [
            sys.executable, "-m", "grpc_tools.protoc",
            f"--proto_path={proto_file.parent}",
            f"--python_out={output_dir}",
            f"--pyi_out={output_dir}",
            f"--grpc_python_out={output_dir}",
            str(proto_file)
        ]
        
        result = subprocess.run(generate_cmd, env=temp_env, capture_output=True, text=True, encoding='utf-8')
        if result.returncode != 0:
            print(f"gRPC code generation failed: {result.stderr}")
            return False
    
    # Import 경로 수정
    print("Fixing import paths...")
    grpc_file = output_dir / "ai_service_pb2_grpc.py"
    if grpc_file.exists():
        with open(grpc_file, 'r', encoding='utf-8') as f:
            content = f.read()
        
        content = content.replace(
            "import ai_service_pb2 as ai__service__pb2",
            "from . import ai_service_pb2 as ai__service__pb2"
        )
        
        with open(grpc_file, 'w', encoding='utf-8') as f:
            f.write(content)
        
        print("Import path fix complete.")
    
    # __init__.py 생성
    init_file = output_dir / "__init__.py"
    init_file.touch() # Create the file if it doesn't exist
    
    print("EC2 compatible gRPC code generation complete!")
    print(f"Generated at: {output_dir}")
    print("Next steps:")
    print("1. Deploy the generated files to EC2")
    print("2. Restart the server on EC2")
    
    return True

if __name__ == "__main__":
    success = generate_grpc_for_ec2()
    sys.exit(0 if success else 1)