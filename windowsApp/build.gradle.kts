plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

group = "com.p59"
version = "1.0.0"


dependencies {
    implementation("com.fazecast:jSerialComm:2.11.4")
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

application {
    mainClass.set("com.p59.windows.MainKt")
    applicationDefaultJvmArgs = buildList {
        add("-Dfile.encoding=UTF-8")
        // Keep the existing Direct3D acceleration on Windows without passing a
        // Windows-only Java2D option to Linux launchers.
        if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
            add("-Dsun.java2d.d3d=true")
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "17"
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.p59.windows.MainKt"
    }
}
