package yaga.k8sservice

import besom.*

enum ImageCoordinates:
  case FullImageName(fullImageName: Input[String])
  case ImageParams(registry: Input[String], name: Input[String], tag: Input.Optional[String])

  def toImageName: Output[String] = this match
    case FullImageName(fullImageName)     => fullImageName.asOutput()
    case ImageParams(registry, name, tag) => p"${registry.asOutput()}/${name.asOutput()}:${tag.asOptionOutput().getOrElse("latest")}"

object ImageCoordinates:
  def apply(
      registry: Input[String],
      name: Input[String],
      tag: Input.Optional[String]
  ): ImageCoordinates =
    ImageParams(registry, name, tag)

  def apply(fullImageName: Input[String]): ImageCoordinates = FullImageName(fullImageName)

enum ImagePlatform:
  case LinuxAmd64
  case LinuxArm64
  case WindowsAmd64
  case WindowsArm64
  case DarwinAmd64
  case DarwinArm64
  case Other(platform: String)

  def toPlatformString: String = this match
    case LinuxAmd64      => "linux/amd64"
    case LinuxArm64      => "linux/arm64"
    case WindowsAmd64    => "windows/amd64"
    case WindowsArm64    => "windows/arm64"
    case DarwinAmd64     => "darwin/amd64"
    case DarwinArm64     => "darwin/arm64"
    case Other(platform) => platform
