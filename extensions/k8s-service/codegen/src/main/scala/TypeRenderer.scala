package yaga.codegen.k8sservice

import tastyquery.Types.*
import tastyquery.Contexts.*
import tastyquery.Symbols.*

import yaga.codegen.core.generator.scalameta.ref

import yaga.codegen.core.generator.TypeRenderer as CoreTypeRenderer

class TypeRenderer(packagePrefixParts: Seq[String], apiSymbols: Set[Symbol]) extends CoreTypeRenderer(packagePrefixParts, apiSymbols):
  import CoreTypeRenderer.notSupported
  
  override def typeToCode(tpe: Type)(using Context): meta.Type =
    tpe match
      case t: AppliedType if t.tycon.showBasic == "yaga.k8sservice.ServiceReference" =>
        val argTypes = t.args.map:
          case arg: Type =>
            //typeToCode(arg)
            serrviceReferenceArgTypeAsCode(arg)
          case _: WildcardTypeArg =>
            notSupported("wildcard type parameter")

        val typeRef = scala.meta.Type.Select(ref("yaga", "k8sservice"), scala.meta.Type.Name("ServiceRef"))

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
          if apiSymbols.contains(sym) then
            packagePrefixParts ++ basicPrefixParts
          else
            basicPrefixParts
        meta.Type.Select(
          absolutePackageRef(shiftedPrefixParts),
          meta.Type.Name(t.name.toString.stripSuffix("$"))
        )
      case t =>
        notSupported(t)
    
