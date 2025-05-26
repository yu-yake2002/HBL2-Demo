package hbl2demo

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._

// AME LazyModule
class AMEModule(implicit p: Parameters) extends LazyModule {
  // Create diplomatic nodes if needed
  // For now, we'll keep it simple without diplomatic nodes
  
  lazy val module = new AMEImp(this)
}

// AME Object for easier instantiation
object AMEModule {
  def apply()(implicit p: Parameters): AMEModule = {
    val ame = LazyModule(new AMEModule())
    ame
  }
} 