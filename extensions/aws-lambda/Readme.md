# AWS Lambdas

Lambdas are defined by extending one of `yaga.extensions.aws.lambda.LambdaHandler` and `yaga.extensions.aws.lambda.LambdaAsyncHandler` classes.
The required type parameters for the extended class are:
  * configuration/initialization requirement - required for the lambda to set up its internals
  * input - data coming in an event
  * output - result returned by the lambda in response to an event 