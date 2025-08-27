package yaga.codegen.k8sservice

import tastyquery.Contexts.*
import tastyquery.Types.*
import tastyquery.Symbols.*

class ModelExtractor extends yaga.codegen.core.extractor.ModelExtractor:
  protected val referencedSchemaableSymbols = collection.mutable.Set.empty[ClassSymbol]

  override def isBuiltinClass(ref: TypeRef) =
    ref.showBasic == "yaga.k8sservice.ServiceReference" || super.isBuiltinClass(ref)

  override def traverseType(tpe: Type)(using Context) =
    tpe match
      case t: AppliedType if t.tycon.showBasic == "yaga.k8sservice.ServiceReference" =>
        val typeArg = t.args match
          case Seq(arg: Type) => arg
          case _ => notSupported(s"yaga.k8sservice.ServiceReference type ${t.showBasic} has unsupported type argument(s)") // TODO better message

        typeArg match
          case ref: TypeRef =>
            val cls = ref.optSymbol match
              case Some(sym) => sym.asClass
                referencedSchemaableSymbols += sym.asClass
              case None => notSupported(s"yaga.k8sservice.ServiceReference type ${t.showBasic} has unsupported type argument: ${ref.showBasic}") // TODO better message
          case _ =>
            notSupported(s"yaga.k8sservice.ServiceReference type ${t.showBasic} has unsupported type argument: ${typeArg.showBasic}") // TODO better message
            
      case _ =>
        super.traverseType(tpe)

  def collectSchemaableTypes(rootTypes: Seq[Type])(using Context) =
    referencedSchemaableSymbols.clear()
    typesToVisit.clear()
    rootTypes.foreach(enqueueType)
    while typesToVisit.nonEmpty do
      val tpe = typesToVisit.dequeue()
      traverseType(tpe)
    referencedSchemaableSymbols.toSet
