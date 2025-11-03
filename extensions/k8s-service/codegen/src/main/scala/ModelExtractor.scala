package yaga.codegen.k8sservice

import tastyquery.Contexts.*
import tastyquery.Types.*
import tastyquery.Symbols.*

import yaga.codegen.core.extractor.ModelExtractor as CoreModelExtractor

class ModelExtractor extends CoreModelExtractor:
  protected val referencedSchemaableSymbols = collection.mutable.Set.empty[ClassSymbol]

  override def isBuiltinClass(ref: TypeRef)(using Context) =
    // Check if type is ServiceReference or a subtype
    ref.optSymbol match
      case Some(sym: ClassSymbol) =>
        def hasServiceReferenceParent(cls: ClassSymbol): Boolean =
          val packageParts = CoreModelExtractor.ownerPackageNamesChain(cls.owner)
          val fullName = (packageParts :+ cls.name.toString).mkString(".")
          fullName == "yaga.k8sservice.ServiceReference" ||
          cls.parents.exists {
            case parent: TypeRef =>
              parent.optSymbol match
                case Some(parentSym: ClassSymbol) => hasServiceReferenceParent(parentSym)
                case _                            => false
            case parent: AppliedType =>
              // Handle generic parents like ServiceReference[E]
              parent.tycon match
                case tyconRef: TypeRef =>
                  tyconRef.optSymbol match
                    case Some(parentSym: ClassSymbol) => hasServiceReferenceParent(parentSym)
                    case _                            => false
                case _ => false
            case _ => false
          }
        hasServiceReferenceParent(sym) || super.isBuiltinClass(ref)
      case _ => super.isBuiltinClass(ref)

  private def isServiceReferenceType(tycon: Type)(using ctx: Context): Boolean =
    // Check if type is ServiceReference or a subtype by examining the type hierarchy
    tycon match
      case ref: TypeRef =>
        ref.optSymbol match
          case Some(sym: ClassSymbol) =>
            def hasServiceReferenceParent(cls: ClassSymbol): Boolean =
              val packageParts = CoreModelExtractor.ownerPackageNamesChain(cls.owner)
              val fullName = (packageParts :+ cls.name.toString).mkString(".")
              fullName == "yaga.k8sservice.ServiceReference" ||
              cls.parents.exists {
                case parent: TypeRef =>
                  parent.optSymbol match
                    case Some(parentSym: ClassSymbol) => hasServiceReferenceParent(parentSym)
                    case _                            => false
                case parent: AppliedType =>
                  // Handle generic parents like ServiceReference[E]
                  parent.tycon match
                    case tyconRef: TypeRef =>
                      tyconRef.optSymbol match
                        case Some(parentSym: ClassSymbol) => hasServiceReferenceParent(parentSym)
                        case _                            => false
                    case _ => false
                case _ => false
              }
            hasServiceReferenceParent(sym)
          case _ => false
      case _ => false

  override def traverseType(tpe: Type)(using Context): Unit =
    tpe match
      case t: AppliedType if isServiceReferenceType(t.tycon) =>
        val typeArg = t.args match
          case Seq(arg: Type) => arg
          case _ =>
            notSupported(s"ServiceReference subtype ${t.showBasic} has unsupported type argument(s)") // TODO better message

        typeArg match
          case ref: TermRef =>
            // Concrete endpoint type (like ProductsEndpoints.type)
            val cls = ref.optSymbol match
              case Some(sym) =>
                sym.moduleClass match
                  case Some(module) =>
                    referencedSchemaableSymbols += module
                  case None =>
                    notSupported(
                      s"ServiceReference subtype ${t.showBasic} has unsupported type argument: ${ref}"
                    ) // TODO better message
              case None =>
                notSupported(
                  s"ServiceReference subtype ${t.showBasic} has unsupported type argument: ${ref}"
                ) // TODO better message

          case _: TypeParamRef =>
            // Type parameter (like E in ServiceReference[E]) - skip, not a concrete type
            ()

          case ref: TypeRef if ref.prefix.isInstanceOf[ThisType] =>
            // Type parameter reference (like OpenApiServiceReference.this.E) - skip
            ()

          case _ =>
            notSupported(
              s"ServiceReference subtype ${t.showBasic} has unsupported type argument: ${typeArg}"
            ) // TODO better message

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
