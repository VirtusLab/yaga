package yaga.kubernetes

import besom.*
import besom.json.*
import besom.api.{kubernetes => k8s}
import java.util.Base64

def dockerSecretFromEcrToken(resourceName: NonEmptyString, namespace: Input[String], secretName: Input[String], registry: Input[String], authToken: Input[String])(using Context): Output[k8s.core.v1.Secret] = {
  val dockerAuths = json"""{
    "auths": {
      ${registry.asOutput()}: {
        "auth": ${authToken.asOutput()}
      }
    }
  }""".map(_.prettyPrint)

  val dockerSecret = k8s.core.v1.Secret(resourceName, k8s.core.v1.SecretArgs(
    metadata = k8s.meta.v1.inputs.ObjectMetaArgs(
      name = secretName,
      namespace = namespace,
      annotations = Map(
        "pulumi.com/patchForce" -> "true"
      )
    ),
    `type` = "kubernetes.io/dockerconfigjson",

    stringData = Map(
      ".dockerconfigjson" -> dockerAuths.map(_.toString)
    )
  ))

  dockerSecret
}