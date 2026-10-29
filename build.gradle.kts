// AGP 9 trae Kotlin integrado: no hace falta el plugin org.jetbrains.kotlin.android.
plugins {
    id("com.android.application") version "9.5.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
    id("com.google.devtools.ksp") version "2.3.12" apply false
}
