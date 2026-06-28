plugins {
    kotlin("jvm") version "2.4.0"
    id("com.gradleup.shadow") version "9.4.2"
}

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.xenondevs.xyz/releases")
    maven("https://repo.extendedclip.com/releases/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("net.kyori:adventure-text-minimessage:5.1.1")
    compileOnly("me.clip:placeholderapi:2.12.2")
    compileOnly("com.h2database:h2:2.3.232")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("xyz.xenondevs.invui:invui:2.1.0")
    implementation("xyz.xenondevs.invui:invui-kotlin:2.1.0")
}

kotlin {
    jvmToolchain(25)
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.shadowJar {
    archiveClassifier.set("")
    exclude("io/papermc/**")
    exclude("net/kyori/**")
    exclude("me/clip/**")
    // 排除 InvUI 未使用的调色板文件（16MB）
    exclude("colors.bin")
}

tasks.processResources {
    val props = mapOf("version" to version)
    filesMatching("plugin.yml") {
        expand(props)
    }
}
