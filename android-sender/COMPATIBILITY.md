# Android Build Compatibility

This project intentionally uses a conservative stable toolchain:

- Gradle: `8.7`
- Android Gradle Plugin (AGP): `8.5.2`
- Kotlin plugin: `1.9.24`

Reason: avoid preview-version resolution errors like
`Cannot select root node ... as a variant`.
