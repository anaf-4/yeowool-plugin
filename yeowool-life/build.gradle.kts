dependencies {
    compileOnly("io.papermc.paper:paper-api:${rootProject.property("paperApiVersion")}")
    compileOnly(project(":yeowool-core"))
    compileOnly("com.github.LoneDev6:API-ItemsAdder:${rootProject.property("itemsAdderApiVersion")}")
    compileOnly("net.momirealms:custom-fishing:2.3.24")
    testImplementation("io.papermc.paper:paper-api:${rootProject.property("paperApiVersion")}")
    testImplementation(project(":yeowool-core"))
}
