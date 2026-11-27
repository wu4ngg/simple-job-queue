import { dag, Workspace, Directory, Container, object, func, File, Service, check } from "@dagger.io/dagger"

const JDK_IMAGE = "eclipse-temurin:21.0.13.1_2-jdk-noble"
const JRE_IMAGE = "eclipse-temurin:21.0.13.1_2-jre-noble"
const POSTGRES_IMAGE = "postgres:17.12"
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
  private postgres(): Service {
    return dag
      .container()
      .from(POSTGRES_IMAGE)
      .withEnvVariable("POSTGRES_DB", "test")
      .withEnvVariable("POSTGRES_USER", "test")
      .withEnvVariable("POSTGRES_PASSWORD", "test")
      .withExposedPort(5432)
      .asService()
  }
  @func()
  @check()
  async test(): Promise<void> {
    await this.gradle()
      .withServiceBinding("db", this.postgres())
      .withEnvVariable("TEST_DB_URL", "jdbc:postgresql://db:5432/test")
      .withEnvVariable("TEST_DB_USER", "test")
      .withEnvVariable("TEST_DB_PASSWORD", "test")
      .withExec(["sh", "./gradlew", "test", "--no-daemon"])
      .sync()
  }
  @func()
  build(): File {
    return this.gradle()
        .withExec(["sh", "./gradlew", "assemble", "--no-daemon"])
        .file("build/libs/simple-job-queue-all.jar")
  }
}
