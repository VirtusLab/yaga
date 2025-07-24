package yaga.k8sservice

import scala.annotation.ConstantAnnotation

class ServerApiSchema(val schema: String) extends ConstantAnnotation
class ClientApiSchema(val schema: String) extends ConstantAnnotation
