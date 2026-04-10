# WASM Service Example

End-to-end walkthrough of the `yaga-wasm-service-*` stack: two WASM
components chained together (`books-service` serves CRUD over an in-memory
book list, `library-service` calls it via a typed service reference),
built and deployed with a single `pulumi up`.

This example is the parallel of `examples/k8s-service/`, but every service
module is a `crossProject(JVMPlatform, JSPlatform)`, every JS half carries
a `wit/world.wit`, and the generated Kubernetes `Deployment` runs
`wasmtime serve /app/main.wasm` instead of `java -jar`.

## Layout

```
books-endpoints/                # shared endpoint defs (JVM + JS, CrossType.Pure)
books-service/                  # WASM service (.yagaWasmService)
library-service/                # WASM service + client (.yagaWasmService.yagaWasmServiceClient)
infra/                          # Besom program; consumes codegen output from both services
```

## Prerequisites

- sbt 1.11+
- JDK 21+
- [`wasmtime`](https://wasmtime.dev) for the local gate
- [`colima`](https://github.com/abiosoft/colima) with `--kubernetes` for the cluster gate
- [`kubectl`](https://kubernetes.io/docs/tasks/tools/) and [`pulumi`](https://www.pulumi.com/docs/install/)
- A local Docker registry reachable at `localhost:5000` (Colima's registry addon or
  `docker run -d -p 5000:5000 --name registry registry:2`)

You also need the forked scala-wasm toolchain + yaga SDK snapshots on your
local Ivy cache. Run `./publish-local.sh` in the repo root before touching
this example.

## Ground rules the plugin can't set for you

These three rules must be replayed verbatim in every WASM-service project
until the scala-wasm fork lands upstream and the yaga plugin can own them.

### 1. `project/plugins.sbt` pin

```scala
resolvers += "Sonatype Central Snapshots" at "https://central.sonatype.com/repository/maven-snapshots/"

addSbtPlugin("io.github.scala-wasm" % "sbt-scalajs" % "1.20.2-wasm.1-SNAPSHOT")

addSbtPlugin(
  ("org.portable-scala" % "sbt-scalajs-crossproject" % "1.3.2")
    .exclude("org.scala-js", "sbt-scalajs")
)
```

Why:

- `io.github.scala-wasm:sbt-scalajs` is the fork of sbt-scalajs that knows
  how to emit WASM from Scala.js — the upstream plugin can't.
- `sbt-scalajs-crossproject` declares a hard dependency on the upstream
  `org.scala-js:sbt-scalajs` artifact; the `.exclude(...)` keeps sbt from
  trying to resolve two conflicting versions of the same plugin key.
- Pin snapshots exactly, including `1.20.2-wasm.1-SNAPSHOT`. Newer snapshot
  taps have produced `.sjsir` artifacts that are IR-incompatible with the
  published yaga runtime jars.

### 2. `scalaCompilerBridgeBinaryJar` workaround

sbt resolves the compiler bridge based on `scalaVersion`, but hardcodes
`org.scala-lang` as the organization. When `scalaOrganization` is
overridden to `io.github.scala-wasm`, the bridge lookup fails.

The fix is to resolve the bridge jar manually and hand it back to sbt.
See `jsPlatformSettings` in `build.sbt` — copy it verbatim into any new
WASM-service project, or lift it into a shared project-level helper.

### 3. Per-JS-half settings

Every JS half of every WASM service must carry:

```scala
scalaOrganization := "io.github.scala-wasm"
scalaVersion      := "3.8.3-RC1-wasm-bin-SNAPSHOT"
scalaCompilerBridgeBinaryJar := { /* see rule 2 */ }
```

The yaga AutoPlugin deliberately does not touch `scalaOrganization` or
`scalaVersion` because sbt-crossproject composes settings in a way that
makes "apply only to JS" hard to guarantee from inside an AutoPlugin. The
build applies these via the reusable `jsPlatformSettings` val.

## Local run (Gate 1: wasmtime only)

Cheap feedback loop — no cluster needed, no docker build. Closes the
Phase 3 outgoing-handler round trip.

```bash
# 1. Produce the two main.wasm binaries.
#    NOTE: fastLinkJS, not fullLinkJS. The scala-wasm fork's optimizer
#    (engaged by fullLinkJS) is known-broken for tapir/circe-derived code —
#    it crashes with NoSuchElementException: arrayGet(ClassRef(ClassName
#    <java.lang.Object>)) in the wasm binary writer. Upstream confirms
#    fastLinkJS is the supported path today.
sbt booksServiceJS/fastLinkJS libraryServiceJS/fastLinkJS

# 2. Start books-service.
#    -Scli enables the wasi:cli imports (environment, clocks, stdin/out/err)
#    in addition to the default wasi:http/proxy world that `wasmtime serve`
#    provides. Our components import `wasi:cli/environment@0.2.0` to read
#    YAGA_WASM_SERVICE_CONFIG, so without -Scli wasmtime rejects them with
#    "component imports instance `wasi:cli/environment@0.2.0`, but a matching
#    implementation was not found in the linker".
wasmtime serve -Scli -Wgc,function-references,exceptions \
  --addr 127.0.0.1:8080 \
  --env YAGA_WASM_SERVICE_CONFIG='{"greeting":"hi from books"}' \
  books-service/js/target/*/books-service-fastopt/main.wasm

# 3. Start library-service (second terminal, points at books-service).
wasmtime serve -Scli -Wgc,function-references,exceptions \
  --addr 127.0.0.1:8081 \
  --env YAGA_WASM_SERVICE_CONFIG='{"booksRef":{"uri":"http://127.0.0.1:8080"}}' \
  library-service/js/target/*/library-service-fastopt/main.wasm

# 4. Smoke-test books-service.
curl http://127.0.0.1:8080/books
# => [{"title":"The Hobbit","author":"J.R.R. Tolkien"},{"title":"Dune","author":"Frank Herbert"}]

# 5. Smoke-test the chained call — this is the Phase 3 sign-off gate.
curl http://127.0.0.1:8081/library/summary
# => {"count":2,"titles":["The Hobbit","Dune"]}
```

### Schema-break check (compile-time)

Rename `title` to `name` in
`books-endpoints/src/main/scala/example/books/Book.scala` and re-run
`sbt libraryServiceJS/compile`. The compiler should fail inside
`LibraryService.serverLogic` because `books.map(_.title)` no longer
resolves. Revert.

## Cluster run (Gate 2: pulumi up into Colima k3s)

```bash
# 0. One-time setup.
colima start --runtime docker --kubernetes
docker run -d -p 5000:5000 --name registry registry:2    # if you don't use Colima's registry addon
kubectl get nodes                                         # sanity-check the k3s node is Ready

# 1. Codegen + infra compile.
sbt infra/compile
# Generated sources land under:
#   infra/target/scala-3.3.6/src_managed/main/yaga-wasm-service-codegen/{books-service,library-service}/

# 2. Bring up the stack.
pulumi login --local
pulumi stack init dev
pulumi up

# 3. Hit library-service through the cluster.
kubectl -n wasm-demo port-forward svc/library-app-service 8081:8080 &
curl http://127.0.0.1:8081/library/summary
# => {"count":2,"titles":["The Hobbit","Dune"]}
```

### Compile-time schema compatibility check

Comment out `addHandler` from the list returned by
`BooksService.serverEndpoints` in
`books-service/shared/src/main/scala/example/books/BooksService.scala` so
that the server only exposes `listBooks`. Leave `BooksEndpoints.addBook`
untouched.

Re-run `sbt infra/compile`. The `SchemaCompatibility` macro in
`infra.scala` (triggered by `booksApp.asServiceRef[BooksEndpoints]`)
should fail with something like:

```
The OpenAPI schemas are not compatible.
Compatibility issues:
  * incompatible path /books:
    - missing operation for post method
```

The client schema (derived from `BooksEndpoints` via `ExtractEndpoints`
and embedded in the generated `BooksEndpoints.scala` companion under
`infra/target/.../library-service/`) still advertises both `GET /books`
and `POST /books`, while the server schema (derived from
`BooksService.serverEndpoints`) now only has `GET`. Revert when done.

Note: *renaming* `addBook` to `createBook` in `BooksEndpoints.scala` does
NOT exercise `SchemaCompatibility` — tapir derives the OpenAPI
`operationId` from the HTTP method and path, not from the Scala val
name, so both sides still emit the same spec. A val rename is only
detected by normal Scala symbol resolution (the reference in
`BooksService.scala` breaks first). Revert.

```bash
# 4. Tear down.
pulumi destroy
```

## Known caveats

- **No cross-request state.** `wasmtime serve` instantiates a fresh
  component per HTTP request. Any `var` you introduce at object level will
  be reset between calls — use an external store for durable state. This
  is why the books list in `BooksService` is defined inside the endpoint
  handler instead of at object scope.
- **WIT deps.** `books-service/js/wit/world.wit` and
  `library-service/js/wit/world.wit` import `wasi:cli/environment@0.2.0`,
  `wasi:http/types@0.2.0`, and friends. The scala-wasm linker resolves
  these via a `wit/deps/` subtree in each JS half. Populate it with
  [`wkg`](https://github.com/bytecodealliance/wasm-pkg-tools) — e.g.
  `cd books-service/js && wkg wit fetch`. The deps are not committed
  here to keep the example source tree small.
- **`imageSecrets` is non-optional.** Phase 4's generated `*Args` classes
  require a `kubernetes.core.v1.Secret` even when the target registry
  (like `localhost:5000`) needs no auth. `infra.scala` synthesizes a stub
  `dockerconfigjson` Secret to satisfy this.
- **WASM component instantiation cost.** The `wasmtime serve` instance-
  per-request model is fine for a demo but adds latency on every call.
  For production-grade pooling you'll want a different component host.
