dependencies {
    compileOnly("io.papermc.paper:paper-api:${rootProject.property("paperApiVersion")}")
    compileOnly(project(":yeowool-core"))
    compileOnly("net.citizensnpcs:citizensapi:${rootProject.property("citizensVersion")}")
    compileOnly("me.clip:placeholderapi:${rootProject.property("placeholderApiVersion")}")
    // BetterHud's CustomPopupEvent is invoked via reflection (QuestDialogueService), not a
    // compile dependency - its jar has no reliable Maven artifact and is built for a newer JDK
    // than this project compiles with.
    testImplementation("io.papermc.paper:paper-api:${rootProject.property("paperApiVersion")}")
    testImplementation(project(":yeowool-core"))
}
