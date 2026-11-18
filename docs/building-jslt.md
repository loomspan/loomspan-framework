# Build the pinned JSLT dependency

The Framework starter depends on `ai.loomspan.thirdparty:jslt:0.1.15-jackson3-cf30e6d`.
Install this artifact in the Maven repository used by a fresh Framework or
consumer build. The interim coordinate is distributed through this local
installation. You need PowerShell 7 (`pwsh`), Git, JDK 21 (`JAVA_HOME`), Maven
3.9+, and network access to GitHub, the upstream Gradle wrapper distribution, and
the declared dependency repositories.

From the Framework checkout root, run the bootstrap with your default local
Maven repository.

```powershell
pwsh -File ai/scripts/install-jslt.ps1
mvn test
```

The script fetches Schibsted JSLT at commit
`cf30e6d9be247d489dac6bf53bf930322c8fd663`, verifies the checked-out commit,
and uses its Gradle wrapper to run `:core:test :core:jar`. The upstream
core build generates the parser with JavaCC and compiles against Jackson Databind
3.2.2. Maven installs the resulting core JAR with the repository's
[dependency POM](../third-party/jslt/pom.xml). The source and local build are
licensed under Apache 2.0. Framework's Spring Boot BOM manages the Jackson
version selected when building Framework.
[Pinned source](https://github.com/schibsted/jslt/tree/cf30e6d9be247d489dac6bf53bf930322c8fd663)

The default source directory is a new temporary directory. Use
`-BuildDirectory` to select a new path. The script keeps that directory and
its build outputs for inspection after success or failure.

For an isolated verification, choose new source and repository paths. Run
these commands from the Framework checkout root and use the same repository
for your consuming Maven project.

```powershell
$jsltRepository = Join-Path $PWD 'target/jslt-clean-m2'
pwsh -File ai/scripts/install-jslt.ps1 -BuildDirectory target/jslt-clean-source -MavenRepository $jsltRepository
mvn "-Dmaven.repo.local=$jsltRepository" dependency:get '-Dartifact=ai.loomspan.thirdparty:jslt:0.1.15-jackson3-cf30e6d' '-Dtransitive=true'
mvn "-Dmaven.repo.local=$jsltRepository" dependency:tree '-Dincludes=ai.loomspan.thirdparty:jslt,tools.jackson.core:jackson-databind'
mvn "-Dmaven.repo.local=$jsltRepository" '-DskipTests' install
mvn -f path/to/consumer/pom.xml "-Dmaven.repo.local=$jsltRepository" compile
```

The consumer declares the installed `ai.loomspan:loomspan-spring-boot-starter`
dependency. It receives JSLT transitively. Executor source can use
`@SkillExecutor` and `SkillExecutorInvocation` from the supported
[Java API](../agent-skills/loomspan-docs/references/java-api/java-skills.md#reference-only-executor-declaration).
