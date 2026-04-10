package yaga.codegen.wasmservice

import tastyquery.Types.*
import tastyquery.Contexts.*
import tastyquery.Symbols.*

import yaga.codegen.core.generator.scalameta.ref

import yaga.codegen.core.generator.TypeRenderer as CoreTypeRenderer

class TypeRenderer(packagePrefixParts: Seq[String], apiSymbols: Set[Symbol]) extends CoreTypeRenderer(packagePrefixParts, apiSymbols):
  import CoreTypeRenderer.notSupported

  private def isServiceReferenceType(tycon: Type)(using ctx: Context): Boolean =
    // Check if type is ServiceReference or a subtype by examining the type hierarchy
    tycon match
      case ref: TypeRef =>
        ref.optSymbol match
          case Some(sym: ClassSymbol) =>
            // Check if this class or any of its parents is ServiceReference
            def hasServiceReferenceParent(cls: ClassSymbol, tpe: TypeRef): Boolean =
              val packageParts = CoreTypeRenderer.prefixNameParts(tpe.prefix)
              val fullName = (packageParts :+ cls.name.toString).mkString(".")
              fullName == "yaga.wasmservice.ServiceReference" ||
              cls.parents.exists {
                case parent: TypeRef =>
                  parent.optSymbol match
                    case Some(parentSym: ClassSymbol) => hasServiceReferenceParent(parentSym, parent)
                    case _                            => false
                case parent: AppliedType =>
                  // Handle generic parents like ServiceReference[E]
                  parent.tycon match
                    case tyconRef: TypeRef =>
                      tyconRef.optSymbol match
                        case Some(parentSym: ClassSymbol) => hasServiceReferenceParent(parentSym, tyconRef)
                        case _                            => false
                    case _ => false
                case _ => false
              }
            hasServiceReferenceParent(sym, ref)
          case _ => false
      case _ => false

  override def typeToCode(tpe: Type)(using Context): meta.Type =
    tpe match
      case t: AppliedType if isServiceReferenceType(t.tycon) =>
        val argTypes = t.args.map:
          case arg: Type =>
            // typeToCode(arg)
            serrviceReferenceArgTypeAsCode(arg)
          case _: WildcardTypeArg =>
            notSupported("wildcard type parameter")

        val typeRef = scala.meta.Type.Select(ref("yaga", "wasmservice"), scala.meta.Type.Name("ServiceRef"))

        meta.Type.Apply(
          typeRef,
          meta.Type.ArgClause(argTypes)
        )

      case _ => super.typeToCode(tpe)

  def serrviceReferenceArgTypeAsCode(tpe: Type)(using Context): meta.Type =
    // Expecting this to be a reference to an object rather than a class (for now at least) but in codegen we generate a class for that
    tpe match
      case t: TermRef =>
        val sym = t.optSymbol.getOrElse(throw Exception(s"TermRef ${t} has no symbol"))
        val basicPrefixParts = CoreTypeRenderer.prefixNameParts(t.prefix)
        val shiftedPrefixParts =
          if apiSymbols.contains(sym) then packagePrefixParts ++ basicPrefixParts
          else basicPrefixParts
        meta.Type.Select(
          absolutePackageRef(shiftedPrefixParts),
          meta.Type.Name(t.name.toString.stripSuffix("$"))
        )
      case t =>
        notSupported(t)
