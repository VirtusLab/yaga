package yaga.sbt.kubernetes

import sbt.*
import sbt.Keys.*
import java.nio.file.{Path, Files}

object CodegenHelpers {
  def generateModuleApiSources(
    localJarSources: Seq[Path],
    packagePrefix: String,
    outputDir: Path,
    withInfra: Boolean,
    dockerContextPath: Path
  ): Unit = {
    val serviceAppFileName = "MyServiceApp.scala" // TODO

    val serviceAppFileContent = s"""
import yaga.kubernetes.{ServiceApp, DeployableImage}

import besom.*
import besom.api.kubernetes

case class MyServiceAppArgs private(
  appName: Output[String],
  namespace: Output[String],
  image: Output[MyServiceApp.Image],
  imageSecrets: Output[kubernetes.core.v1.Secret]
)

object MyServiceAppArgs {
  def apply(
    appName: Input[String],
    namespace: Input[String],
    image: Input[MyServiceApp.Image],
    imageSecrets: Input[kubernetes.core.v1.Secret]
  ): MyServiceAppArgs = new MyServiceAppArgs(
    appName = appName.asOutput(),
    namespace = namespace.asOutput(),
    image = image.asOutput(),
    imageSecrets = imageSecrets.asOutput()
  )
}

case class MyServiceApp private(
  appName: Output[String],
  serviceName: Output[Option[String]],
  deploymentName: Output[Option[String]],
  namespace: Output[String],
) (using ComponentBase) extends ComponentResource with ServiceApp derives RegistersOutputs

object MyServiceApp {
  case class Image private[MyServiceApp](image: besom.api.docker.Image) extends yaga.kubernetes.DeployableImage[MyServiceApp] derives Encoder:
    def imageReference: Output[String] = image.repoDigest

  object Image:
    val dockerContextPath = "${dockerContextPath}"


  def imageResource(resourceName: NonEmptyString, fullImageName: Input[String], registry: Input[besom.api.docker.inputs.RegistryArgs])(using Context): Output[Image] = {
    val image = besom.api.docker.Image(
      resourceName,
      besom.api.docker.ImageArgs(
        imageName = fullImageName,
        build = besom.api.docker.inputs.DockerBuildArgs(
          context = Image.dockerContextPath,
          platform = "linux/amd64"
        ),
        registry = registry
      )
    )

    image.map(new Image(_))
  }

  def apply(
    name: NonEmptyString,
    args: MyServiceAppArgs,
    opts: ResourceOptsVariant.Component ?=> ComponentResourceOptions = ComponentResourceOptions()
  )(using Context): Output[MyServiceApp] = {
    val typ: ResourceType = "yaga:custom:MyServiceApp" // TODO

    val appLabel = args.appName

    val targetPort = 8080 // TODO

    besom.component(name, typ, opts(using ResourceOptsVariant.Component)) {
      val basicDeploymentArgs = kubernetes.apps.v1.DeploymentArgs(
        metadata = kubernetes.meta.v1.inputs.ObjectMetaArgs(
          name = args.appName,
          namespace = args.namespace
        ),
        spec = kubernetes.apps.v1.inputs.DeploymentSpecArgs(
          replicas = 1,
          selector = kubernetes.meta.v1.inputs.LabelSelectorArgs(
            matchLabels = Map("yaga-app" -> appLabel)
          ),
          template = kubernetes.core.v1.inputs.PodTemplateSpecArgs(
            metadata = kubernetes.meta.v1.inputs.ObjectMetaArgs(
              labels = Map("yaga-app" -> appLabel)
            ),
            spec = kubernetes.core.v1.inputs.PodSpecArgs(
              containers = List(kubernetes.core.v1.inputs.ContainerArgs(
                name = "yaga-app", // TODO
                image = args.image.imageReference, 

                // TODO
                ports = List(kubernetes.core.v1.inputs.ContainerPortArgs(containerPort = targetPort))
              )),
              imagePullSecrets = List(kubernetes.core.v1.inputs.LocalObjectReferenceArgs(name = args.imageSecrets.metadata.name))
            )
          )
        )
      )

      val deploymentArgs = basicDeploymentArgs

      val deployment = kubernetes.apps.v1.Deployment("my-deployment", deploymentArgs)

      val basicServiceArgs = kubernetes.core.v1.ServiceArgs(
        metadata = kubernetes.meta.v1.inputs.ObjectMetaArgs(
          name = args.appName,
          namespace = args.namespace
        ),
        spec = kubernetes.core.v1.inputs.ServiceSpecArgs(
          selector = Map("yaga-app" -> appLabel),


          // TODO
          ports = List(
            kubernetes.core.v1.inputs.ServicePortArgs(
              port = targetPort,
              targetPort = targetPort
            )
          ),

          // TODO
          `type` = "NodePort" // for local dev (e.g., Minikube); LoadBalancer for cloud
          // `type` = "LoadBalancer"
        )
      )

      val basicServiceAccountArgs = kubernetes.core.v1.ServiceAccountArgs(
        metadata = kubernetes.meta.v1.inputs.ObjectMetaArgs(
          name = args.appName,
          namespace = args.namespace
        )
      )

      val serviceAccountArgs = basicServiceAccountArgs

      val serviceArgs = basicServiceArgs

      val service = kubernetes.core.v1.Service("service", serviceArgs)

      new MyServiceApp(
        appName = args.appName,
        serviceName = service.metadata.name,
        deploymentName = deployment.metadata.name,
        namespace = args.namespace,
      )
    }
  }
}
"""

    Files.createDirectories(outputDir)
    Files.write(outputDir / serviceAppFileName, serviceAppFileContent.getBytes)
  }
}