import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.testing.logging.TestLogEvent.*

plugins {
  java
  application
  id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "ssonin"
version = "1.0.0-SNAPSHOT"

repositories {
  mavenCentral()
}

val flywayVersion = "11.7.2"
val junitJupiterVersion = "5.9.1"
val logbackVersion = "1.5.21"
val postgresqlVersion = "42.7.8"
val slf4jVersion = "2.0.17"
val vertxVersion = "5.0.6"

val mainVerticleName = "ssonin.nvstech.App"
val launcherClassName = "io.vertx.launcher.application.VertxApplication"

application {
  mainClass.set(launcherClassName)
}

dependencies {
  implementation(platform("io.vertx:vertx-stack-depchain:$vertxVersion"))

  implementation("ch.qos.logback:logback-classic:${logbackVersion}")
  implementation("io.vertx:vertx-core")
  implementation("io.vertx:vertx-launcher-application")
  implementation("io.vertx:vertx-pg-client")
  implementation("io.vertx:vertx-web")
  implementation("org.flywaydb:flyway-core:${flywayVersion}")
  implementation("org.flywaydb:flyway-database-postgresql:${flywayVersion}")
  implementation("org.slf4j:slf4j-api:${slf4jVersion}")

  runtimeOnly("org.postgresql:postgresql:${postgresqlVersion}")

  testImplementation("io.vertx:vertx-junit5")
  testImplementation("org.junit.jupiter:junit-jupiter:$junitJupiterVersion")

  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
  sourceCompatibility = JavaVersion.VERSION_21
  targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<ShadowJar> {
  archiveClassifier.set("fat")
  manifest {
    attributes(mapOf("Main-Verticle" to mainVerticleName))
  }
  mergeServiceFiles()
}

tasks.withType<Test> {
  useJUnitPlatform()
  testLogging {
    events = setOf(PASSED, SKIPPED, FAILED)
  }
}

tasks.withType<JavaExec> {
  args = listOf(mainVerticleName)
}
