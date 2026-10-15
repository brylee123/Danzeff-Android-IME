# Credits and third-party notices

Danzeff Android IME credits **danzel** and **Jeff Chen**, the original Danzeff keyboard authors, **geecko (Clément G)** for Danzeff-G, and **Sakya** for the Danzeff OSlib port.

- [Danzeff-G](https://github.com/libcg/Danzeff-G): copyright 2006 danzel, 2006 Jeff Chen, and 2012 geecko; BSD 3-Clause. The complete original notice is in [DANZEFF-G-LICENSE.txt](licenses/DANZEFF-G-LICENSE.txt).
- [DanzeffOSLib archive](https://github.com/PSP-Archive/DanzeffOSLib): the Android character table follows its four `modeChar` pages and PSP action ordering. Android rendering, gestures, input service and profiles are independently implemented; original PSP artwork and binaries are not bundled.
- [Danzeff Flipper Lab](https://github.com/brylee123/Danzeff-Flipper-Lab): this project follows its licensing approach: BSD 2-Clause for new project code and preservation of the upstream BSD 3-Clause notice. Its newer Danzeff-G layout differs from this Android table: Android retains the OSlib apostrophe in the top-left symbol slot and zero at the bottom of the bottom-right numeric cell.
- Gradle 8.11.1 wrapper: build tooling distributed under Apache 2.0 and applicable bundled notices. Full distribution LICENSE and NOTICE are preserved in `docs/licenses/GRADLE-LICENSE.txt` and `GRADLE-NOTICE.txt`. This tooling is not packaged in the APK.

Original Android code is covered by the root [LICENSE](../LICENSE). Derived Danzeff layout data retains the upstream BSD 3-Clause notice. The APK includes these credits and both BSD licenses in its assets and exposes them through **Credits & licenses** in the launcher app.

Attribution does not imply endorsement. The original authors' names must not be used to endorse or promote derived products without their prior written permission. Source and binary redistributions must retain the applicable copyright notices, conditions and disclaimers.


Google Sans Regular and Medium v14.000 (Android static builds), unmodified, are bundled under SIL OFL 1.1. See [font license](licenses/GOOGLE-SANS-OFL.txt) and [official release](https://github.com/googlefonts/googlesans/releases/tag/v14.000). The font notice is also included in the APK. This project is not affiliated with Google or Gboard.

The fifth EX+ page is ported from Danzeff Flipper Lab's danzeff_core.c convenience layout. Its full BSD 2-Clause notice is retained in docs/licenses/DANZEFF-FLIPPER-LICENSE.txt and the APK credits. The first four Android pages retain their existing OSlib mapping.


## UI and document rendering

Material Components Android 1.12.0 and AndroidX (Apache 2.0), Markwon 4.6.2 (Apache 2.0), and CommonMark Java 0.15.0 (BSD 2-Clause) provide the UI and native Markdown renderer. Complete notices are bundled in the credits viewer and docs/licenses.
