"""Supplementary checks; these are NOT the teacher's EvaluatorPracticeTest."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory() as folder:
    build = Path(folder) / "classes"
    build.mkdir()
    subprocess.run(["java", "-m", "jdk.compiler/com.sun.tools.javac.Main",
                    "-encoding", "UTF-8", "-d", str(build),
                    *map(str, (ROOT / "src").glob("*.java"))], check=True)

    def run(cls, text, *args):
        return subprocess.run(["java", "-cp", str(build), cls, *args],
                              input=text, text=True, capture_output=True,
                              timeout=30, cwd=folder)

    checks = 0
    for text, args, expected in [
        ("ERROR a\nINFO b\nERROR ERROR\n", (), 2),
        ("WARNING a\nERROR b\nWARNING c", ("WARNING",), 2),
        ("INFO only\n", (), 0),
        ("ERROR café\nWARNING à\n", (), 1),
    ]:
        r = run("FiltreLog", text, *args)
        assert (r.returncode, r.stdout.strip(), r.stderr) == (0, str(expected), ""), r
        checks += 1
    for text in ["", "\n \t\n"]:
        r = run("FiltreLog", text)
        assert r.returncode == 1 and not r.stdout
        assert r.stderr.strip() == "Error: Empty text"
        checks += 1
    r = run("AnalitzadorPrincipal", "")
    assert r.returncode == 0
    assert r.stdout.splitlines()[-1] == "RESULTAT: ERRORS=3 | WARNINGS=3 | EXIT_CODE=0"
    checks += 1
    r = run("AnalitzadorPrincipal", "", "--stdin")
    assert r.returncode == 1
    assert r.stdout.splitlines()[-1] == "RESULTAT: ERRORS=0 | WARNINGS=0 | EXIT_CODE=1"
    for name in ["errors_filtre.log", "errors_filtre_warning.log"]:
        assert (Path(folder) / name).read_text().strip() == "Error: Empty text"
    checks += 1
    r = run("AnalitzadorPrincipal", "ERROR a\nWARNING b\nINFO c\n" * 100000, "--stdin")
    assert r.returncode == 0
    assert r.stdout.splitlines()[-1] == "RESULTAT: ERRORS=100000 | WARNINGS=100000 | EXIT_CODE=0"
    checks += 1
    print(f"PASS: {checks} supplementary checks (including 300,000 log lines).")
