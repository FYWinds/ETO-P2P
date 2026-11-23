"""Run Gradle with the JDK this project builds on and print only what matters.

    uv run tools/build.py [gradle task ...]      default task: build

The global ~/.gradle/gradle.properties points at JDK 21; this project needs the JDK in $ETO_BUILD_JDK, else
~/.jdks/temurin-25.0.5+8. The full output goes to build/build.log; errors, warnings with a source location, failed
tasks and the result line are printed.
"""

from __future__ import annotations

import os
import re
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
JDK = Path(os.environ.get("ETO_BUILD_JDK", Path.home() / ".jdks" / "temurin-25.0.5+8"))
SHOWN = re.compile(
    r"^(e: |w: .*\.kts?:|.*\.java:\d+: |\s+\^|> Task .* FAILED|FAILURE|BUILD |\* What went wrong|Execution failed"
    r"|.*Test.* FAILED|\d+ tests completed|.*Could not (resolve|find)|.*[Ee]xception: |WARNING: )"
)


def main() -> None:
    tasks = sys.argv[1:] or ["build"]
    log = REPO / "build" / "build.log"
    log.parent.mkdir(exist_ok=True)
    command = [str(REPO / "gradlew.bat"), *tasks, f"-Dorg.gradle.java.home={JDK}", "--console=plain"]
    with log.open("w", encoding="utf-8", errors="replace") as out:
        process = subprocess.Popen(
            command, cwd=REPO, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, errors="replace"
        )
        assert process.stdout is not None
        for line in process.stdout:
            out.write(line)
            if SHOWN.match(line):
                print(line.rstrip())
        code = process.wait()
    print(f"gradle {' '.join(tasks)}: exit {code}; full output in {log}")
    sys.exit(code)


if __name__ == "__main__":
    main()
