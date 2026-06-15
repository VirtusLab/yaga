import besom.*
import besom.api.kubernetes
import besom.api.docker

import example.books.{BooksService, BooksServiceArgs}
import example.greeting.{GreetingService, GreetingServiceArgs}
import example.library.{LibraryService, LibraryServiceArgs}

import yaga.wasmservice.{ImageCoordinates, ImagePlatform}

// End-to-end infra program for the WASM service example.
//
// Deploys books-service + library-service onto a local k3s cluster (Colima).
// The generated `BooksService` / `LibraryService` resource classes come from
// `yaga-wasm-service-codegen`, wired in via `.withYagaDependencies(...)` in
// build.sbt — see the generator at
// `extensions/wasm-service/codegen/src/main/scala/ModuleApiGenerator.scala`
// for the exact emitted shape.
//
// NOTE: the generated `BooksServiceArgs` / `LibraryServiceArgs` require a
// non-optional `imageSecrets: Input[kubernetes.core.v1.Secret]` even when
// the target registry is unauthenticated. We synthesize a stub
// dockerconfigjson secret to satisfy that contract without touching the
// generator. Track as a Phase 4 follow-up: allow `Option[Secret]` on the
// generated args so local/unauthenticated registries can pass `None`.
@main def main = Pulumi.run {
  // Registry host - set via REGISTRY_HOST env var (from deploy-k3s-wasm.sh)
  // Both docker (build) and k3s-wasm (pull) can reach this via socket_vmnet networking
  val registryHost = sys.env.getOrElse("REGISTRY_HOST", "localhost:5000")
  val namespaceName = "wasm-demo"

  val namespace = kubernetes.core.v1.Namespace(
    namespaceName,
    kubernetes.core.v1.NamespaceArgs(
      metadata = kubernetes.meta.v1.inputs.ObjectMetaArgs(name = namespaceName)
    )
  )

  // Stub image-pull secret. Colima's default registry at localhost:5000 runs
  // unauthenticated, so the actual credentials don't matter — only the Secret
  // resource needs to exist for the generated Deployment to reference it.
  val stubDockerSecret = kubernetes.core.v1.Secret(
    "wasm-demo-docker-secret",
    kubernetes.core.v1.SecretArgs(
      metadata = kubernetes.meta.v1.inputs.ObjectMetaArgs(
        name = "wasm-demo-docker-secret",
        namespace = namespaceName
      ),
      `type` = "kubernetes.io/dockerconfigjson",
      stringData = Map(
        ".dockerconfigjson" -> s"""{"auths":{"$registryHost":{}}}"""
      )
    ),
    opts(dependsOn = namespace)
  )

  val booksImage = BooksService.imageResource(
    resourceName = "books-image",
    imageCoordinates = ImageCoordinates(s"$registryHost/books-service:dev"),
    registry = docker.inputs.RegistryArgs(),
    // Apple Silicon / colima runs arm64 natively. LinuxAmd64 would force rosetta
    // emulation in-VM, which crashes wasmtime on startup with OOMKilled/exit 137
    // during component instantiation. Match the cluster arch.
    platform = ImagePlatform.LinuxArm64
  )

  val libraryImage = LibraryService.imageResource(
    resourceName = "library-image",
    imageCoordinates = ImageCoordinates(s"$registryHost/library-service:dev"),
    registry = docker.inputs.RegistryArgs(),
    // Apple Silicon / colima runs arm64 natively. LinuxAmd64 would force rosetta
    // emulation in-VM, which crashes wasmtime on startup with OOMKilled/exit 137
    // during component instantiation. Match the cluster arch.
    platform = ImagePlatform.LinuxArm64
  )

  val booksApp = BooksService(
    "books-app",
    BooksServiceArgs(
      namespace    = namespaceName,
      image        = booksImage,
      imageSecrets = stubDockerSecret,
      runConfig    = example.books.BooksConfig(greeting = "hi from books")
    )
  )

  val greetingImage = GreetingService.imageResource(
    resourceName = "greeting-image",
    imageCoordinates = ImageCoordinates(s"$registryHost/greeting-service:dev"),
    registry = docker.inputs.RegistryArgs(),
    platform = ImagePlatform.LinuxArm64
  )

  val greetingApp = GreetingService(
    "greeting-app",
    GreetingServiceArgs(
      namespace    = namespaceName,
      image        = greetingImage,
      imageSecrets = stubDockerSecret,
      runConfig    = example.greeting.GreetingConfig(defaultName = "World")
    )
  )

  val libraryApp = LibraryService(
    "library-app",
    LibraryServiceArgs(
      namespace    = namespaceName,
      image        = libraryImage,
      imageSecrets = stubDockerSecret,
      runConfig =
        for booksRef <- booksApp.asServiceRef[example.books.BooksEndpoints]
            greetingRef <- greetingApp.asServiceRef[example.greeting.GreetingEndpoints]
        yield example.library.LibraryConfig(booksRef = booksRef, greetingRef = greetingRef)
    )
  )

  Stack(namespace, stubDockerSecret, booksImage, booksApp, greetingImage, greetingApp, libraryImage, libraryApp).exports(
    booksServiceName        = booksApp.flatMap(_.serviceName),
    booksDeploymentName     = booksApp.flatMap(_.deploymentName),
    greetingServiceName     = greetingApp.flatMap(_.serviceName),
    greetingDeploymentName  = greetingApp.flatMap(_.deploymentName),
    libraryServiceName      = libraryApp.flatMap(_.serviceName),
    libraryDeploymentName   = libraryApp.flatMap(_.deploymentName)
  )
}
