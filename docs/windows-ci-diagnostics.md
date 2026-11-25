# Windows CI diagnostics

This branch investigates the Windows checks on Snapshot commit `f79c0504`.

The Windows build samples the Gradle test worker every two minutes with
`jcmd Thread.print`. Look for `Sampling Gradle workers` in the build step.
The diagnostic build step has a 30-minute limit so its logs become available
even if a test stays blocked.

The snapshot DLL passed isolated placement, color and concurrent-rendering
checks. The full Vector workflow now builds and tests the current commit on
each platform. It downloads the native artifact from its own workflow run.
On Windows failure it checks the event decoder and reads this runner's
Java Application Error events.

Dispatch `release.yml` on the branch to run the Snapshot checks and rehearse
publication to the runner's local Maven repository.

[Snapshot](https://github.com/DigitalSmile/goldberry/actions/runs/45622791691) ·
[Windows build](https://github.com/DigitalSmile/goldberry/actions/runs/45660407957) ·
[Windows SVG smoke run](https://github.com/DigitalSmile/goldberry/actions/runs/45661023893)
