import { dag, Workspace, Directory, Container, object, func, File } from "@dagger.io/dagger"

const JDK_IMAGE = "eclipse-temurin:21.0.13.1_2-jdk-noble"
const JRE_IMAGE = "eclipse-temurin:21.0.13.1_2-jre-noble"

@object()
export class SimpleJobQueue {
  /** Image the build runs on. */

  source: Directory

  constructor(
    ws: Workspace,
  ) {
    this.source = ws.directory("/", {
      exclude: ["**/node_modules", "**/.git", "**/dist", "**/.dagger", "build", ".gradle", ".kotlin", ".idea", "CLAUDE.md"],
    })
  }

  private gradle(): Container {
    return dag
      .container()
      .from(JDK_IMAGE)
      .withMountedCache("/root/.gradle", dag.cacheVolume("gradle-home"))
      .withDirectory("/src", this.source)
      .withWorkdir("/src")
  }

  @func()
  build(): File {
    return this.gradle()
        .withExec(["sh", "./gradlew", "assemble", "--no-daemon"])
        .file("build/libs/simple-job-queue-all.jar")
  }
}
