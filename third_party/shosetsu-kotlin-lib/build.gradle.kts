import org.gradle.jvm.tasks.Jar

group = "app.shosetsu.lib"
version = "1.4.0"
description = "Kotlin library for shosetsu"

plugins {
	alias(libs.plugins.kotlin.jvm)
	alias(libs.plugins.dokka)
	alias(libs.plugins.kotlin.serialization)
	`maven-publish`
}

val dokkaJar by tasks.registering(Jar::class) {
	group = JavaBasePlugin.DOCUMENTATION_GROUP
	description = "Assembles Kotlin docs with Dokka"
}

kotlin {
	jvmToolchain(11)
}

publishing {
	publications {
		create<MavenPublication>("maven") {
			groupId = project.group.toString()
			artifactId = "kotlin-lib"
			version = project.version.toString()

			from(components["kotlin"])
		}
	}
}

repositories {
	mavenCentral()
	maven("https://jitpack.io")
}

dependencies {
	dokkaHtmlPlugin(libs.dokka.kajp)

	// ### Core Libraries
	api(libs.kotlinx.serialization.json)
	api(libs.kotlinx.datetime) // For MPP time control

	// java only
	api(libs.jsoup)
	api(libs.luaj.jse)
	api(libs.okhttp)
	implementation(libs.guava)


	// ### Testing Libraries
	testImplementation(kotlin("stdlib"))
	testImplementation(kotlin("stdlib-jdk8"))

	testImplementation(kotlin("reflect"))

	testImplementation(kotlin("test"))
}
