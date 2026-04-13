package yaga.sbt.k8sservice

import sbt.*
import sbt.Keys.*
import sbtcrossproject.CrossProject
import sbtcrossproject.CrossPlugin.autoImport.*
import scalajscrossproject.ScalaJSCrossPlugin.autoImport.*
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport.*
import java.nio.file.{Files, Path}

case class YagaWasmServiceDependency(
  crossProject: CrossProject,
  outputSubdirName: Option[String],
  packagePrefix: String,
  withInfra: Boolean,
  wasmRuntime: WasmRuntime = WasmRuntime.EmbeddedWasmtime
) extends yaga.sbt.YagaDependency {
  import K8sServicePluginKeys.deployableJars

  override def addSelfToProject(baseProject: Project): Project = {
    val jvmProject: Project = crossProject.jvm
    val jsProject: Project = crossProject.js

    // `withInfra` is known at task-definition time, so we branch the task graph here
    // rather than inside a single task body. That keeps fastLinkJS off the critical
    // path for model-only dependencies and sidesteps the sbt task-linter warning
    // about `.value` lookups inside an `if`.
    val codegenTask: Def.Initialize[Task[Seq[File]]] =
      if (withInfra) infraCodegenTask(jvmProject, jsProject, baseProject)
      else modelOnlyCodegenTask(jvmProject, baseProject)

    baseProject.settings(
      yaga.sbt.YagaPlugin.autoImport.yagaGeneratedSources ++= codegenTask.value,
      libraryDependencies ++= {
        if (withInfra) Seq(YagaK8sServicePlugin.yagaWasmServiceBesomDep)
        else Seq.empty
      }
    )
  }

  private def outputDirFor(baseProject: Project, projectName: String): String =
    outputSubdirName.getOrElse(projectName)

  private def modelOnlyCodegenTask(
    jvmProject: Project,
    baseProject: Project
  ): Def.Initialize[Task[Seq[File]]] = Def.task {
    val projectName = (jvmProject / name).value
    val baseProjectName = (baseProject / name).value
    val outputSubdirectoryName = outputDirFor(baseProject, projectName)
    val codegenOutputDir = (baseProject / Compile / sourceManaged).value /
      "yaga-wasm-service-codegen" / outputSubdirectoryName

    val jvmJars: Seq[Path] = (jvmProject / deployableJars).value
    val jvmChanged = (jvmProject / deployableJars).outputFileChanges.hasChanges

    implicit val log: Logger = streams.value.log

    val needsRun = jvmChanged || !Files.exists(codegenOutputDir.toPath)

    if (needsRun) {
      log.info(s"Yaga - wasm service: Generating module API sources from ${projectName} for ${baseProjectName}")
      CodegenHelpers.generateWasmModuleApiSources(
        localJarSources = jvmJars,
        packagePrefix = packagePrefix,
        outputDir = codegenOutputDir.toPath,
        withInfra = false,
        dockerContextPath = None,
        wasmRuntime = wasmRuntime
      )
    }

    (codegenOutputDir ** "*.scala").get
  }

  private def infraCodegenTask(
    jvmProject: Project,
    jsProject: Project,
    baseProject: Project
  ): Def.Initialize[Task[Seq[File]]] = Def.task {
    val projectName = (jvmProject / name).value
    val baseProjectName = (baseProject / name).value
    val outputSubdirectoryName = outputDirFor(baseProject, projectName)
    val codegenOutputDir = (baseProject / Compile / sourceManaged).value /
      "yaga-wasm-service-codegen" / outputSubdirectoryName

    val jvmJars: Seq[Path] = (jvmProject / deployableJars).value
    val jvmChanged = (jvmProject / deployableJars).outputFileChanges.hasChanges

    // Force fastLinkJS (produces main.wasm). Discard the returned Report; we just
    // need the side-effect and the output directory.
    //
    // NOTE: we deliberately use fastLinkJS here, not fullLinkJS. The scala-wasm fork's
    // optimizer (engaged by fullLinkJS) is known to be incompatible with the current
    // wasm backend — it crashes with
    //   java.util.NoSuchElementException: key not found: arrayGet(ClassRef(ClassName<java.lang.Object>))
    // in BinaryWriter/TextWriter for tapir/circe-derived code. Upstream (Rikito)
    // confirmed fullLinkJS is not expected to work on the wasm target today;
    // fastLinkJS is the supported path. fastLinkJS skips the optimizer but still
    // produces a valid main.wasm via the wasm backend.
    val _ = (jsProject / Compile / fastLinkJS).value
    val wasmOutputDir: File =
      (jsProject / Compile / fastLinkJS / scalaJSLinkerOutputDirectory).value
    // NOTE: we can't use `.outputFileChanges.hasChanges` on `fastLinkJS` because the
    // scala-wasm fork of sbt-scalajs does not declare `Def.fileOutputs` on that task —
    // so sbt leaves `fastLinkJS / outputFileStamps` undefined. Rely on jvmChanged +
    // output-dir existence instead. main.wasm is copied into the stage dir below
    // unconditionally, so the docker context always reflects the latest fastLinkJS run.

    implicit val log: Logger = streams.value.log

    val mainWasm: File = wasmOutputDir / "main.wasm"
    require(
      mainWasm.exists(),
      s"yagaWasmServiceInfra: expected ${mainWasm} to exist after fastLinkJS, got nothing. " +
        s"Check scalaJSLinkerConfig.withTargetPureWasm(true) + WIT directory on ${(jsProject / name).value}."
    )

    val stageDir = (baseProject / target).value /
      "yaga-wasm-docker-context" / outputSubdirectoryName
    IO.createDirectory(stageDir)
    IO.copyFile(mainWasm, stageDir / "main.wasm")
    // Generate Dockerfile based on the chosen runtime variant.
    val dockerfile = wasmRuntime match {
      case WasmRuntime.EmbeddedWasmtime =>
        // NOTE on the Dockerfile: there is no official `ghcr.io/bytecodealliance/wasmtime`
        // image. For the yaga example / CI path, we build a small Debian-based image that
        // bakes the pinned wasmtime release in and runs it as a normal process. Identical
        // runtime semantics to the local Gate-1 `wasmtime serve` command.
        //
        // `uname -m` inside the container matches wasmtime's release-asset naming
        // (`aarch64` / `x86_64`), so the same Dockerfile builds on both Apple-silicon
        // colima and amd64 CI without a BuildKit TARGETARCH dance.
        //
        // `-Scli` is required because our components import `wasi:cli/environment@0.2.0`
        // to read `YAGA_WASM_SERVICE_CONFIG`. `--addr 0.0.0.0:8080` is required because
        // wasmtime serve defaults to 127.0.0.1, which a k8s Service cannot reach; the
        // generated Service/ContainerPort both hardcode 8080 on the pod side (see
        // ModuleApiGenerator.scala — targetPort = 8080 TODO).
        val wasmtimeVersion = "v43.0.0"
        s"""FROM debian:bookworm-slim
           |RUN apt-get update \\
           | && apt-get install -y --no-install-recommends curl xz-utils ca-certificates \\
           | && arch=$$(uname -m) \\
           | && curl -sSL https://github.com/bytecodealliance/wasmtime/releases/download/$wasmtimeVersion/wasmtime-$wasmtimeVersion-$${arch}-linux.tar.xz \\
           |    | tar -xJ -C /usr/local/bin --strip-components=1 --wildcards '*/wasmtime' \\
           | && apt-get purge -y curl xz-utils \\
           | && apt-get autoremove -y \\
           | && rm -rf /var/lib/apt/lists/*
           |COPY main.wasm /app/main.wasm
           |ENTRYPOINT ["wasmtime","serve","-Scli","-Sinherit-env","-Wgc,function-references,exceptions","--addr","0.0.0.0:8080","/app/main.wasm"]
           |""".stripMargin

      case WasmRuntime.RuntimeClass(_) =>
        // Minimal OCI artifact for clusters with native WASM runtime support via
        // containerd shims (e.g., containerd-shim-wasmtime-v1 from runwasi).
        //
        // The image contains only the WASM binary. The cluster's RuntimeClass handler
        // (wasmtime, wasmedge, spin, etc.) executes it directly. The WASM component
        // must implement wasi:http/proxy@0.2.0 (already the case for yaga services).
        //
        // Environment variables (YAGA_WASM_SERVICE_CONFIG) are passed through by the
        // runtime via wasi:cli/environment — no special container setup needed.
        s"""FROM scratch
           |COPY main.wasm /
           |ENTRYPOINT ["/main.wasm"]
           |""".stripMargin
    }
    IO.write(stageDir / "Dockerfile", dockerfile)
    val dockerContextPath: Path = stageDir.toPath

    val needsRun = jvmChanged || !Files.exists(codegenOutputDir.toPath)

    if (needsRun) {
      log.info(s"Yaga - wasm service: Generating module API sources from ${projectName} for ${baseProjectName}")
      CodegenHelpers.generateWasmModuleApiSources(
        localJarSources = jvmJars,
        packagePrefix = packagePrefix,
        outputDir = codegenOutputDir.toPath,
        withInfra = true,
        dockerContextPath = Some(dockerContextPath),
        wasmRuntime = wasmRuntime
      )
    }

    (codegenOutputDir ** "*.scala").get
  }
}
