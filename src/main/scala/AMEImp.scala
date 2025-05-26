package hbl2demo

import chisel3._
import chisel3.util._
// import freechips.rocketchip.config._
import org.chipsalliance.cde.config.{Parameters, Field}
import AME._
import AME.{AME, connectPort}
import utility.sram._
import common._
import RegFile._
import MMAU._
import Expander._
import ScoreBoard._
import MLU._
import freechips.rocketchip.diplomacy._

// AME Parameters case class
case class AMEParams(
  numTrRegs: Int = 2,      // Number of Tr registers
  numAccRegs: Int = 1,     // Number of Acc registers
  dataWidth: Int = 32,     // Data width
  matrixSize: Int = 16     // Matrix size (NxN)
)

// AME Configuration Object
// object AMEConfigKey {
//   val AMEConfig = new Field[AMEParams]
// }
case object AMEConfigKey extends Field[AMEParams]// 需要定义(AMEParam())

// AME Bundle trait and class
trait AMEParameter {
  val trRegNum: Int = 2
  val accRegNum: Int = 1
  val dataWidth: Int = 32
  val matrixSize: Int = 16
}

class AMEBundle extends Bundle with AMEParameter

// AME IO Bundle
class AMEIO(implicit p: Parameters) extends AMEBundle {
  val Uop_io = new Uop_IO
  val writeAll = new RegFileAllWrite_IO
  val readAll = new RegFileAllRead_IO
  val MLU_L2_io = new MLU_L2_IO
  val sigDone = Output(Bool())
}

// AME Implementation
class AMEImp(outer: AMEModule)(implicit p: Parameters) extends LazyModuleImp(outer) {
  val io = IO(new AMEIO)
  
  // Get configuration parameters
  val params = p(AMEConfigKey)
  
  // Instantiate core module
  val amecore = Module(new AMECore)

  // Connect core module
  amecore.io.Uop_io <> io.Uop_io
  amecore.io.writeAll <> io.writeAll
  amecore.io.readAll <> io.readAll
  amecore.io.MLU_L2_io <> io.MLU_L2_io
  io.sigDone := amecore.io.sigDone
}

// AME Core Module
class AMECore(implicit p: Parameters) extends Module with AMEParameter {
  val io = IO(new AMEIO)

  // Instantiate submodules
  val subMMAU = Module(new MMAU)
  val subRegFile = Module(new RegFile)
  val subExpander = Module(new Expander)
  val subScoreBoard = Module(new ScoreBoard)
  val subMLU = Module(new MLU)

  // Debug signal
  io.sigDone := subExpander.io.sigDone

  // Connect MLU to L2
  io.MLU_L2_io <> subMLU.io.MLU_L2_io

  // RegFile connections
  subRegFile.io := DontCare
  io.writeAll <> subRegFile.io.writeAll(1)
  io.readAll <> subRegFile.io.readAll(1)

  // Expander connections
  io.Uop_io <> subExpander.io.Uop_io

  // MMAU and RegFile connections
  connectPort.toTrReadPort(
    subRegFile.io.readTr(0),
    subMMAU.io.Ops_io.ms1,
    subMMAU.io.actPortReadA,
    subMMAU.io.addrReadA,
    subMMAU.io.vecA
  )

  connectPort.toTrReadPort(
    subRegFile.io.readTr(1),
    subMMAU.io.Ops_io.ms2,
    subMMAU.io.actPortReadB,
    subMMAU.io.addrReadB,
    subMMAU.io.vecB
  )

  connectPort.toAccReadPort(
    subRegFile.io.readAcc(0),
    subMMAU.io.Ops_io.md,
    subMMAU.io.actPortReadC,
    subMMAU.io.addrReadC,
    subMMAU.io.vecCin
  )

  connectPort.toAccWritePort(
    subRegFile.io.writeAcc(0),
    subMMAU.io.Ops_io.md,
    subMMAU.io.actPortWriteC,
    subMMAU.io.addrWriteC,
    subMMAU.io.vecCout,
    subMMAU.io.sigEnWriteC
  )

  // MMAU and Expander connections
  subMMAU.io.FSM_MMAU_io <> subExpander.io.FSM_MMAU_io

  // RegFile and MLU connections
  subRegFile.io.writeAll(0) <> subMLU.io.RegFileAllWrite_io

  // Expander and ScoreBoard connections
  subExpander.io.ScoreboardVisit_io <> subScoreBoard.io.ScoreboardVisit_io

  // Expander and MLU connections
  subMLU.io.FSM_MLU_io <> subExpander.io.FSM_MLU_io
} 