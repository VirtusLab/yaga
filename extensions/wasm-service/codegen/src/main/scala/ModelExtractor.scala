package yaga.codegen.wasmservice

import tastyquery.Contexts.*
import tastyquery.Types.*
import tastyquery.Symbols.*

import yaga.codegen.core.extractor.ModelExtractor as CoreModelExtractor

class ModelExtractor extends CoreModelExtractor:
  protected val referencedSchemaableSymbols = collection.mutable.Set.empty[ClassSymbol]

  // Walk the parent chain of `cls` looking for yaga.wasmservice.ServiceReference.
  //
  // NOTE: we wrap `.optSymbol` in a try/catch because tasty-query may fail to
  // resolve certain synthetic/internal TypeRefs on Scala stdlib classes
  // (e.g. `scala.Product/T`) during recursion. For the ServiceReference check,
  // any unresolvable parent is definitely not our marker trait — swallow and skip.
  private def hasServiceReferenceParent(cls: ClassSymbol)(using Context): Boolean =
    val packageParts = CoreModelExtractor.ownerPackageNamesChain(cls.owner)
    val fullName = (packageParts :+ cls.name.toString).mkString(".")
    if fullName == "yaga.wasmservice.ServiceReference" then return true
    cls.parents.exists { parent =>
      try
        parent match
          case ref: TypeRef =>
            ref.optSymbol match
              case Some(parentSym: ClassSymbol) => hasServiceReferenceParent(parentSym)
              case _                            => false
          case applied: AppliedType =>
            // Handle generic parents like ServiceReference[E]
            applied.tycon match
              case tyconRef: TypeRef =>
                tyconRef.optSymbol match
                  case Some(parentSym: ClassSymbol) => hasServiceReferenceParent(parentSym)
                  case _                            => false
              case _ => false
          case _ => false
      catch
        case _: tastyquery.Exceptions.MemberNotFoundException => false
    }

  override def isBuiltinClass(ref: TypeRef)(using Context) =
    // Check if type is ServiceReference or a subtype.
    //
    // Resolving a TypeRef's symbol can throw MemberNotFoundException when
    // tasty-query encounters scala stdlib members it can't load from the
    // current classpath (e.g. `scala.Predef` when the test classpath only
    // supplies our local jar). Any type we can't resolve is, by definition,
    // NOT `yaga.wasmservice.ServiceReference` — fall back to super's builtin
    // check which handles these cases robustly.
    try
      ref.optSymbol match
        case Some(sym: ClassSymbol) =>
          hasServiceReferenceParent(sym) || super.isBuiltinClass(ref)
        case _ => super.isBuiltinClass(ref)
    catch
      case _: tastyquery.Exceptions.MemberNotFoundException =>
        super.isBuiltinClass(ref)

  private def isServiceReferenceType(tycon: Type)(using ctx: Context): Boolean =
    // Check if type is ServiceReference or a subtype by examining the type hierarchy
    tycon match
      case ref: TypeRef =>
        ref.optSymbol match
          case Some(sym: ClassSymbol) => hasServiceReferenceParent(sym)
          case _                      => false
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
