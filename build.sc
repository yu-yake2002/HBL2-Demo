import mill._
import scalalib._
import os.Path
import publish._
import $file.common
import $file.CoupledL2.`rocket-chip`.common
import $file.CoupledL2.`rocket-chip`.common
import $file.CoupledL2.`rocket-chip`.cde.common
import $file.CoupledL2.`rocket-chip`.hardfloat.build
import $file.CoupledL2.common  // Import the coupledL2 module

val defaultVersions = Map(
  "chisel3" -> "3.6.0",
  "chisel3-plugin" -> "3.6.0",
  "chiseltest" -> "0.6.2",
  "scala" -> "2.13.10",
)
object ivys{
  val sv = "2.13.15"
  val chisel3 = ivy"org.chipsalliance::chisel:6.6.0"
  val chisel3Plugin = ivy"org.chipsalliance:::chisel-plugin:6.6.0"
  val chiseltest = ivy"edu.berkeley.cs::chiseltest:6.0.0"
  val scalatest = ivy"org.scalatest::scalatest:3.2.19"
}

def getVersion(dep: String, org: String = "edu.berkeley.cs", cross: Boolean = false) = {
  val version = sys.env.getOrElse(dep + "Version", defaultVersions(dep))
  if (cross)
    ivy"$org:::$dep:$version"
  else
    ivy"$org::$dep:$version"
}

trait HasChisel extends ScalaModule {
  def chiselModule: Option[ScalaModule] = None

  def chiselPluginJar: T[Option[PathRef]] = None

  def chiselIvy: Option[Dep] = Some(getVersion("chisel3"))

  def chiselPluginIvy: Option[Dep] = Some(getVersion("chisel3-plugin", cross=true))

  override def scalaVersion = defaultVersions("scala")

  override def scalacOptions = super.scalacOptions() ++
    Agg("-language:reflectiveCalls", "-Ymacro-annotations", "-Ytasty-reader")

  override def ivyDeps = super.ivyDeps() ++ Agg(chiselIvy.get)

  override def scalacPluginIvyDeps = super.scalacPluginIvyDeps() ++ Agg(chiselPluginIvy.get)
}

object rocketchip extends CoupledL2.`rocket-chip`.common.RocketChipModule with HasChisel {

  val rcPath = os.pwd / "CoupledL2" / "rocket-chip"
  override def millSourcePath = rcPath

  def mainargsIvy = ivy"com.lihaoyi::mainargs:0.5.0"

  def json4sJacksonIvy = ivy"org.json4s::json4s-jackson:4.0.5"

  object macros extends CoupledL2.`rocket-chip`.common.MacrosModule with HasChisel {
    def scalaReflectIvy = ivy"org.scala-lang:scala-reflect:${scalaVersion}"
  }

  object cde extends CoupledL2.`rocket-chip`.cde.common.CDEModule with HasChisel {
    override def millSourcePath = rcPath / "cde" / "cde"
  }

  object hardfloat extends CoupledL2.`rocket-chip`.hardfloat.common.HardfloatModule with HasChisel {
    override def millSourcePath = rcPath / "hardfloat" / "hardfloat"
  }

  def macrosModule = macros

  def hardfloatModule = hardfloat

  def cdeModule = cde

}

object utility extends SbtModule with HasChisel {
  override def millSourcePath = os.pwd / "CoupledL2" / "utility"

  override def moduleDeps = super.moduleDeps ++ Seq(rocketchip)
 }

object huancun extends SbtModule with HasChisel {
  override def millSourcePath = os.pwd / "CoupledL2" / "HuanCun"
  override def moduleDeps = super.moduleDeps ++ Seq(
    rocketchip, utility
  )
}

object coupledL2 extends SbtModule with HasChisel {
  // Specify the source path for the coupledL2 module
  override def millSourcePath = os.pwd  / "CoupledL2"
  override def moduleDeps = super.moduleDeps ++ Seq(
    rocketchip, utility,huancun
  )
}

object hbl2demo extends SbtModule with HasChisel with millbuild.common.hbl2demoModule{
  // Import and use the coupledL2 module

  override def millSourcePath = millOuterCtx.millSourcePath
  def rocketModule: ScalaModule = rocketchip
  def utilityModule: ScalaModule = utility
  def huancunModule: ScalaModule = huancun
  def coupledL2Module: ScalaModule = coupledL2
  override def ivyDeps = super.ivyDeps() ++ Agg(
    getVersion("chiseltest"),
  )

  // Add the coupledL2 module as a dependency
  override def moduleDeps = super.moduleDeps ++ Seq(coupledL2Module, rocketModule, utilityModule, huancunModule)

  object test extends SbtModuleTests with TestModule.ScalaTest {
  override def ivyDeps = super.ivyDeps() ++ Agg(
    getVersion("chiseltest"),
  )
}
}
