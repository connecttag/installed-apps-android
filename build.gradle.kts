plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
}

group = "org.connecttag.libs.kotlin"
version = "1.1.0"

kotlin {
    jvmToolchain(21)
}

android {
    namespace = "org.connecttag.platform.installedapps.android"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    implementation(libs.androidx.ktx)
    implementation(libs.kotlinx.coroutines.core)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "org.connecttag.libs.kotlin"
                artifactId = "installed-apps-android"
                version = "1.1.0"

                pom {
                    name.set("ConnectTag Installed Apps Android")
                    description.set("Android library for querying, filtering, and resolving installed applications, launcher activities, and default apps with Package Visibility support")
                    url.set("https://github.com/connecttag/installed-apps-android")
                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    developers {
                        developer {
                            id.set("connecttagye")
                            name.set("Connect Tag")
                            email.set("connecttagye@gmail.com")
                        }
                    }
                    scm {
                        connection.set("scm:git:https://github.com/connecttag/installed-apps-android.git")
                        developerConnection.set("scm:git:ssh://git@github.com:connecttag/installed-apps-android.git")
                        url.set("https://github.com/connecttag/installed-apps-android/tree/main")
                    }
                }
            }
        }
    }
}
