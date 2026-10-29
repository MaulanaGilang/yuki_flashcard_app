plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.compose.compiler)
}

android {
  namespace = "app.yuki.core.designsystem"
  compileSdk = 37
  defaultConfig {
    minSdk = 26
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
  }
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  api(project(":core:model"))
  val composeBom = platform(libs.androidx.compose.bom)
  api(composeBom)
  api(libs.androidx.compose.ui)
  api(libs.androidx.compose.foundation)
  api(libs.androidx.compose.material3)
  api(libs.androidx.compose.ui.tooling.preview)
  debugImplementation(libs.androidx.compose.ui.tooling)
}
