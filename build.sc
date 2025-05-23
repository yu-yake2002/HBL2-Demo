import mill._
import scalalib._
import os.Path
import publish._
import $file.common
import $file.CoupledL2.`rocket-chip`.common
import $file.CoupledL2.`rocket-chip`.common
import $file.CoupledL2.`rocket-chip`.cde.common
import $file.CoupledL2.`rocket-chip`.hardfloat.build
import $file.CoupledL2.common
import $file.AME.common

val defaultScalaVersion = "2.13.15"

def defaultVersions = Map(
  "chisel" -> ivy"org.chipsalliance::chisel:6.6.0",
  "chisel-plugin" -> ivy"org.chipsalliance:::chisel-plugin:6.6.0",
  "chiseltest" -> ivy"edu.berkeley.cs::chiseltest:6.0.0",
)

trait HasChisel extends ScalaModule {
  def chiselModule: Option[ScalaModule] = None

  def chiselPluginJar: T[Option[PathRef]] = None

  def chiselIvy: Option[Dep] = Some(defaultVersions("chisel"))

  def chiselPluginIvy: Option[Dep] = Some(defaultVersions("chisel-plugin"))

  override def scalaVersion = defaultScalaVersion

  override def scalacOptions = super.scalacOptions() ++
    Agg("-language:reflectiveCalls", "-Ymacro-annotations", "-Ytasty-reader")

  override def ivyDeps = super.ivyDeps() ++ Agg(chiselIvy.get)

  override def scalacPluginIvyDeps = super.scalacPluginIvyDeps() ++ Agg(chiselPluginIvy.get)
}

object rocketchip extends CoupledL2.`rocket-chip`.common.RocketChipModule with HasChisel {

  val rcPath = os.pwd / "CoupledL2" / "rocket-chip"
  override def millSourcePath = rcPath

  def mainargsIvy = ivy"com.lihaoyi::mainargs:0.7.0"

  def json4sJacksonIvy = ivy"org.json4s::json4s-jackson:4.0.7"

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
    rocketchip,
    utility
  )
}

object coupledL2 extends SbtModule with HasChisel {
  // Specify the source path for the coupledL2 module
  override def millSourcePath = os.pwd / "CoupledL2"
  override def moduleDeps = super.moduleDeps ++ Seq(
    rocketchip,
    utility,
    huancun
  )
}

object fpu extends SbtModule with HasChisel {
  override def millSourcePath = os.pwd / "AME" / "FP8fpu"
  // override def moduleDeps = super.moduleDeps ++ Seq(rocketchip)
}

object ame extends SbtModule with HasChisel {
  override def millSourcePath = os.pwd / "AME"
  override def moduleDeps = super.moduleDeps ++ Seq(
    rocketchip,
    utility,
    fpu
  )
}

object hbl2demo extends SbtModule with HasChisel with millbuild.common.hbl2demoModule {
  override def millSourcePath = millOuterCtx.millSourcePath
  def rocketModule:    ScalaModule = rocketchip
  def utilityModule:   ScalaModule = utility
  def huancunModule:   ScalaModule = huancun
  def coupledL2Module: ScalaModule = coupledL2
  def ameModule:       ScalaModule = ame
  def fpuModule:       ScalaModule = fpu

  // Add the coupledL2 module as a dependency
  override def moduleDeps = super.moduleDeps ++ Seq(
    coupledL2Module,
    rocketModule,
    utilityModule,
    huancunModule,
    ameModule,
    fpuModule
  )

  object test extends SbtModuleTests with TestModule.ScalaTest {
    override def ivyDeps = super.ivyDeps() ++ Agg(
      defaultVersions("chiseltest")
    )
  }
  override def scalacOptions = super.scalacOptions() ++ Agg("-deprecation", "-feature")
}
