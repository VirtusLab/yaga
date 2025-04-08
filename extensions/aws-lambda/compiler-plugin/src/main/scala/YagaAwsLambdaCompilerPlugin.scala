package yaga.compilerplugin.aws.lambda

import dotty.tools.dotc.ast.tpd.*
import dotty.tools.dotc.core.*
import dotty.tools.dotc.core.Annotations.Annotation
import dotty.tools.dotc.core.Contexts.Context
import dotty.tools.dotc.core.Constants.Constant
import dotty.tools.dotc.core.Decorators.*
import dotty.tools.dotc.core.StdNames.*
import dotty.tools.dotc.core.Symbols.*
import dotty.tools.dotc.core.Types.Type
import dotty.tools.dotc.plugins.{PluginPhase, StandardPlugin}
import dotty.tools.dotc.typer.TyperPhase
import dotty.tools.dotc.transform.sjs.PrepJSInterop

class YagaAwsLambdaCompilerPlugin extends StandardPlugin {
  val name: String = "YagaAwsLambdaCompilerPlugin"
  override val description: String = "Runs additional transformations required for integration with Yaga's AWS Lambdas"

  def init(options: List[String]): List[PluginPhase] =
    (new YagaAwsLambdaHandlerExtractionPhase) :: Nil
}

class YagaAwsLambdaHandlerExtractionPhase extends PluginPhase {
  val phaseName = "yagaAwsLambdaHandlerExtraction"

  override val runsAfter  = Set(TyperPhase.name)
  override val runsBefore = Set(PrepJSInterop.name)

  private def inheritsFromHandlerMarker(symbol: Symbol)(implicit ctx: Context): Boolean = {
    symbol.derivesFrom(syncHandlerClassSymbol) || symbol.derivesFrom(asyncHandlerClassSymbol)
  }

  private var syncHandlerClassSymbol: Symbol = _
  private var asyncHandlerClassSymbol: Symbol = _
  private var jsExportTopLevelType: Type = _

  override def prepareForUnit(tree: Tree)(using ctx: Context): Context = {
    syncHandlerClassSymbol = requiredClass("yaga.extensions.aws.lambda.LambdaHandler")
    asyncHandlerClassSymbol = requiredClass("yaga.extensions.aws.lambda.LambdaAsyncHandler")
    jsExportTopLevelType = requiredClassRef("scala.scalajs.js.annotation.JSExportTopLevel")
    ctx
  }

  override def transformTypeDef(tree: TypeDef)(using Context): Tree =
    tree match
      case tree @ TypeDef(name, temp: Template) if tree.symbol.isClass && inheritsFromHandlerMarker(tree.symbol) =>
        val exportedName = tree.symbol.showFullName.toString.split('.').mkString("_")
        val annot = Annotation(New(jsExportTopLevelType, List(Literal(Constant(exportedName)))))
        tree.symbol.addAnnotation(annot)
      case _ =>

    tree
}
