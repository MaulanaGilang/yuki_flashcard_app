plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.serialization)
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  api(project(":core:model"))
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.serialization.json)
}
