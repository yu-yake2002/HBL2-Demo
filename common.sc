import mill._
import scalalib._

trait hbl2demoModule extends ScalaModule {

  def coupledL2Module: ScalaModule

  def rocketModule: ScalaModule

  def utilityModule: ScalaModule

  def huancunModule: ScalaModule

  def ameModule: ScalaModule

  def fpuModule: ScalaModule

  override def moduleDeps = super.moduleDeps ++ Seq(
    coupledL2Module,
    rocketModule,
    utilityModule,
    huancunModule,
    ameModule,
    fpuModule)
}
