package yaga.codegen.k8sservice

import tastyquery.Contexts.*
import tastyquery.Types.*
import tastyquery.Symbols.*

import yaga.codegen.core.extractor.ModelExtractor as CoreModelExtractor

class ModelExtractor extends CoreModelExtractor:
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
          case ref: TermRef =>
            val cls = ref.optSymbol match
              case Some(sym) =>
                sym.moduleClass match
                  case Some(module) =>
                    referencedSchemaableSymbols += module
                  case None =>
                    notSupported(s"yaga.k8sservice.ServiceReference type ${t.showBasic} has unsupported type argument: ${ref}") // TODO better message
              case None => notSupported(s"yaga.k8sservice.ServiceReference type ${t.showBasic} has unsupported type argument: ${ref}") // TODO better message
            
          case _ =>
            notSupported(s"yaga.k8sservice.ServiceReference type ${t.showBasic} has unsupported type argument: ${typeArg}") // TODO better message
            
      case _ =>
        super.traverseType(tpe)

  def collectSchemaableTypeSymbols(rootTypes: Seq[Type])(using Context) =
    referencedSchemaableSymbols.clear()
    typesToVisit.clear()
    rootTypes.foreach(enqueueType)
    while typesToVisit.nonEmpty do
      val tpe = typesToVisit.dequeue()
      traverseType(tpe)
    referencedSchemaableSymbols.toSet
