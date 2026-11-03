#!/usr/bin/env python3
"""Wrapper invoking scripts/generate_gantt.py"""
import os
import subprocess
import sys

script_path = os.path.join(os.path.dirname(__file__), "..", "..", "scripts", "generate_gantt.py")
if os.path.exists(script_path):
    sys.exit(subprocess.call([sys.executable, script_path] + sys.argv[1:]))
else:
    # Fallback to direct import or execution
    from scripts import generate_gantt
    generate_gantt.main()
