package yaga.wasmservice

trait ServerApiSchema[A]:
  type Schema <: String & Singleton

trait ClientApiSchema[A]:
  type Schema <: String & Singleton
