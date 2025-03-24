package com.virtuslab.parent_lambda

import scala.concurrent.ExecutionContext.Implicits.global
import besom.json.*
import yaga.extensions.aws.lambda.{LambdaClient, LambdaHandle, LambdaAsyncHandler}
import com.virtuslab.child_lambda_a
import com.virtuslab.child_lambda_b

case class Config(
  childLambdaA: LambdaHandle[child_lambda_a.Bar, Unit],
  childLambdaB: LambdaHandle[child_lambda_b.Bar, child_lambda_b.Baz]
) derives JsonFormat

case class Qux(
  str: String = "abcb"
) derives JsonFormat

class ParentLambda extends LambdaAsyncHandler[Config, Qux, String]:
  val lambdaClient = LambdaClient()
  println("Parent lambda initialized")

  override def handleInput(input: Qux) =
    val childLambdaAInput = child_lambda_a.Bar(child_lambda_a.Foo(str = input.str))

    val childLambdaBInput = child_lambda_b.Bar(child_lambda_b.Foo(str = input.str))

    // println("Triggering child-lambda-a")
    println("Invoking child-lambda-b")
    for {
      // _ <- lambdaClient.triggerEvent(config.childLambdaA, childLambdaAInput)
      // _ = println("Triggered child-lambda-a")
      baz <- lambdaClient.invokeWithResponse(config.childLambdaB, childLambdaBInput)
    } yield {
      println(s"Response from child-lambda-b: ${baz}")
      "Processing completed"
    }

  // override def handleInput(input: Qux) =
  //   // Async call to child-lambda-a
  //   val childLambdaAInput = child_lambda_a.Bar(child_lambda_a.Foo(str = input.str))
  //   lambdaClient.invokeAsyncUnsafe(config.childLambdaA, childLambdaAInput)

  //   // Sync call to child-lambda-b
  //   val childLambdaBInput = child_lambda_b.Bar(child_lambda_b.Foo(str = input.str))
  //   val baz = lambdaClient.invokeSyncUnsafe(config.childLambdaB, childLambdaBInput)
  //   println(s"Response from child-lambda-b: ${baz}")

  //   "Processing completed"
