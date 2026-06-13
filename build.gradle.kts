plugins {
    alias(libs.plugins.agp.app) apply false
    alias(libs.plugins.kotlin.android) apply false
}

tasks.register<Delete>("clean").configure {
    delete(layout.buildDirectory)
}
