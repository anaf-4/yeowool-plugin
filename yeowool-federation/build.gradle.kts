dependencies {
    compileOnly("io.papermc.paper:paper-api:${rootProject.property("paperApiVersion")}")
    compileOnly(project(":yeowool-core"))
    testImplementation("io.papermc.paper:paper-api:${rootProject.property("paperApiVersion")}")
    testImplementation(project(":yeowool-core"))
}
