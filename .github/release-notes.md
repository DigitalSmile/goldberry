{{changes}}

## Install

Goldberry {{version}} is on Maven Central under the group `dev.goldberry`, and needs JDK 25.

```groovy
dependencies {
    implementation platform('dev.goldberry:goldberry-bom:{{version}}')
    implementation 'dev.goldberry:goldberry'
    // The native library: one classifier per platform you run on.
    runtimeOnly 'dev.goldberry:goldberry-natives::linux-x64'
    runtimeOnly 'dev.goldberry:goldberry-natives::linux-aarch64'
    runtimeOnly 'dev.goldberry:goldberry-natives::macos-aarch64'
    runtimeOnly 'dev.goldberry:goldberry-natives::windows-x64'
}
```

The Maven form, and the optional modules (`goldberry-html`, `goldberry-emoji`,
`goldberry-gpu`, `goldberry-media`), are in
[Installing](https://goldberry.dev/docs/getting-started/installing.html).

## Downloads

The showcase, every widget in one application, as a GraalVM native image: one file
per platform, attached below. Unpack the tarballs; the `.exe` runs as it is.

**Changelog:** [CHANGELOG.md](https://github.com/DigitalSmile/goldberry/blob/v{{version}}/CHANGELOG.md)
