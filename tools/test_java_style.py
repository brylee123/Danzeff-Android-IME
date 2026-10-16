"""Regression checks for the style gate; run with python -m unittest discover -s tools."""

import importlib.util
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

TOOLS = Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location("format_java", TOOLS / "format_java.py")
STYLE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(STYLE)
JAVA = STYLE.java_executable(None)


class JavaStyleToolTest(unittest.TestCase):
    def check_source(self, source):
        with tempfile.TemporaryDirectory(prefix="java style fixtures ") as work:
            root = Path(work)
            file = root / "Example.java"
            file.write_text(source, encoding="utf-8")
            manifest = root / "sources.txt"
            manifest.write_text(str(file) + "\n", encoding="utf-8")
            return subprocess.run(
                [JAVA, "--source", "17", str(TOOLS / "JavaStyleCheck.java"), str(manifest)],
                capture_output=True,
                text=True,
                check=False,
            )

    def test_accepts_records_final_lambdas_resources_and_flat_else_if(self):
        result = self.check_source(
            """
import java.io.ByteArrayInputStream;
import java.util.function.Function;
record Example(String value) {
    void androidTypeWithoutSdk(final android.view.View view) {}
    String run(final String text) throws Exception {
        final Function<String, String> fn = (final String word) -> word.trim();
        try (final ByteArrayInputStream in = new ByteArrayInputStream(new byte[0])) {
            if (in.read() == -1) { return fn.apply(text); }
            else if (text.isEmpty()) { return "var inside a string is fine"; }
            else { for (int i = 0, j = 2; i < j; i++) { System.out.println(i); } }
        } catch (final RuntimeException error) { throw error; }
        final ByteArrayInputStream existing = new ByteArrayInputStream(new byte[0]);
        try (existing) { return value; }
    }
}
"""
        )
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_rejects_all_documented_syntax_rules(self):
        result = self.check_source(
            """
import java.util.*;
import java.io.ByteArrayInputStream;
class Example {
    int first, second;
    Example(String name) {}
    void run(int argument) throws Exception {
        var text = "hello";
        java.util.function.Function<String, String> fn = word -> word.trim();
        try (ByteArrayInputStream in = new ByteArrayInputStream(new byte[0])) {
            if (argument > 0) System.out.println(text);
            else System.out.println(argument);
            for (int i = 0; i < 2; i++) System.out.println(i);
            for (String word : new String[0]) System.out.println(word);
            while (argument-- > 0) System.out.println(argument);
            do argument++; while (argument < 1);
        } catch (RuntimeException error) { throw error; }
    }
}
"""
        )
        self.assertNotEqual(0, result.returncode)
        for message in (
            "Parameter must be final",
            "Lambda parameters must have explicit types",
            "Resource declarations must be final",
            "Use an explicit type instead of var",
            "Use explicit imports",
            "Declare one field/local per statement",
            "Control-flow body must use braces",
        ):
            self.assertIn(message, result.stderr)

    def test_formatter_rejects_drift_and_fixes_paths_with_spaces_offline(self):
        jar = STYLE.ROOT / ".gradle/style/google-java-format-1.28.0-all-deps.jar"
        self.assertTrue(jar.exists(), "Run tools/format_java.py once to populate the formatter cache.")
        with tempfile.TemporaryDirectory(prefix="java style offline ") as work:
            root = Path(work)
            (root / "tools").mkdir()
            (root / "core/src").mkdir(parents=True)
            for file in ("format_java.py", "JavaStyleCheck.java"):
                shutil.copy2(TOOLS / file, root / "tools" / file)
            file = root / "core/src/Example.java"
            original = "final class Example{int twice(final int value){return value*2;}}\n"
            file.write_text(original, encoding="utf-8")
            command = [
                sys.executable,
                str(root / "tools/format_java.py"),
                "--java", JAVA,
                "--formatter-jar", str(jar),
            ]
            drift = subprocess.run(command + ["--check"], capture_output=True, text=True)
            self.assertNotEqual(0, drift.returncode, drift.stdout + drift.stderr)
            self.assertEqual(original, file.read_text(encoding="utf-8"))
            fixed = subprocess.run(command, capture_output=True, text=True)
            self.assertEqual(0, fixed.returncode, fixed.stdout + fixed.stderr)
            self.assertNotEqual(original, file.read_text(encoding="utf-8"))
            clean = subprocess.run(command + ["--check"], capture_output=True, text=True)
            self.assertEqual(0, clean.returncode, clean.stdout + clean.stderr)

    def test_pinned_checksum_and_source_discovery(self):
        with tempfile.TemporaryDirectory() as work:
            root = Path(work)
            corrupt = root / "formatter.jar"
            corrupt.write_bytes(b"incorrect formatter")
            with self.assertRaisesRegex(ValueError, "SHA-256 mismatch"):
                STYLE.formatter(corrupt, root)
            original_root = STYLE.ROOT
            try:
                STYLE.ROOT = root
                for name in (
                    "app/src/main/java/A.java", "core/src/B.java", "test/C.java",
                    "benchmark/src/D.java", "tools/E.java", "app/build/generated/Excluded.java",
                ):
                    path = root / name
                    path.parent.mkdir(parents=True, exist_ok=True)
                    path.write_text("", encoding="utf-8")
                self.assertEqual(5, len(STYLE.sources()))
            finally:
                STYLE.ROOT = original_root


if __name__ == "__main__":
    unittest.main()
