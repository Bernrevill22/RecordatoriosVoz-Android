// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    // El plugin de Android (ajusta la versión si usas una más nueva de Android Studio)
    id("com.android.application") version "8.4.0" apply false
    id("com.android.library") version "8.4.0" apply false

    // Kotlin 2.0.0 es la versión recomendada para evitar errores de compatibilidad
    id("org.jetbrains.kotlin.android") version "2.0.0" apply false

    // El nuevo Plugin de Compose (obligatorio para Kotlin 2.0+)
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0" apply false

    // KSP: Necesario para que Room funcione con Kotlin 2.0
    id("com.google.devtools.ksp") version "2.0.0-1.0.21" apply false
}