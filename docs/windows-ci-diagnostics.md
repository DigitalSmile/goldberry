# Windows CI diagnostics

This branch investigates the Windows checks on Snapshot commit `f79c0504`.

The Windows build samples the Gradle test worker every two minutes with
`jcmd Thread.print`. Look for `Sampling Gradle workers` in the build step.
The diagnostic build step has a 30-minute limit so its logs become available
even if a test stays blocked.

The Vector workflow records Java Application Error events after a Windows
failure. Its manual `windows_smoke` option uses the Windows DLL from the
same Snapshot and runs the placement, color and concurrent-rendering tests.
Events belong to the current runner. The first successful smoke run passed
all three test groups and recorded no Java crash event.

[Snapshot](https://github.com/DigitalSmile/goldberry/actions/runs/45622791691) ·
[Windows build](https://github.com/DigitalSmile/goldberry/actions/runs/45660407957) ·
[Windows SVG smoke run](https://github.com/DigitalSmile/goldberry/actions/runs/45661023893)
