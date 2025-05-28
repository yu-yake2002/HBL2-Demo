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
import freechips.rocketchip.tilelink._
import coupledL2.{MatrixField, MatrixKey, MatrixDataBundle}

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
  val sigDone = Output(Bool())
  val matrix_data_in = Vec(8, Flipped(DecoupledIO(new MatrixDataBundle())))  
  // val matrix_data_in = Vec(8, DecoupledIO(new MatrixDataBundle()))  // For M channel responses
}

// AME Implementation
class AMEImp(outer: AMEModule)(implicit p: Parameters) extends LazyModuleImp(outer) {
  val io = IO(new AMEIO)
  
  // Get configuration parameters
  val params = p(AMEConfigKey)
  
  // Get TileLink interfaces for all 8 matrix nodes
  val (tls, edges) = outer.matrix_nodes.map(_.out.head).unzip
  
  // Instantiate core module
  // val amecore = Module(new AMECore)

  // Connect core module
  // amecore.io.Uop_io <> io.Uop_io
  // amecore.io.writeAll <> io.writeAll
  // amecore.io.readAll <> io.readAll
  // io.sigDone := amecore.io.sigDone

  val subMMAU = Module(new MMAU)
  val subRegFile = Module(new RegFile)
  val subExpander = Module(new Expander)
  val subScoreBoard = Module(new ScoreBoard)
  val subMLU = Module(new MLU)

  // Debug signal
  io.sigDone := subExpander.io.sigDone

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

  // Connect MLU_L2_io to TileLink interface
  // val subMLU = subMLU//Module(new MLU())//amecore.subMLU

    // Initialize MLU interfaces with DontCare
  // subMLU.io.FSM_MLU_io := DontCare
  // subMLU.io.MLU_L2_io.Cacheline_ReadBack_io := DontCare
  // subMLU.io.RegFileAllWrite_io := DontCare
  
  // M channel handling (for Get responses)
  val matrix_data_in = io.matrix_data_in

  for (i <- 0 until 8) {
    matrix_data_in(i).ready := true.B  // AME is receiver in M channel

    when(matrix_data_in(i).valid) {
      subMLU.io.MLU_L2_io.Cacheline_ReadBack_io(i).valid := true.B
      subMLU.io.MLU_L2_io.Cacheline_ReadBack_io(i).data := matrix_data_in(i).bits.data.data
      subMLU.io.MLU_L2_io.Cacheline_ReadBack_io(i).id := matrix_data_in(i).bits.sourceId
    }.otherwise {
      subMLU.io.MLU_L2_io.Cacheline_ReadBack_io(i).valid := false.B
      subMLU.io.MLU_L2_io.Cacheline_ReadBack_io(i).data := 0.U
      subMLU.io.MLU_L2_io.Cacheline_ReadBack_io(i).id := 0.U
    }
  }

  // Connect each MLU Cacheline interface to its corresponding TileLink node
  for (i <- 0 until 8) {
    println(s"Connecting MLU Cacheline interface $i to TileLink node")
    val tl = tls(i)
    val edge = edges(i)

    // A channel - Get request handling
    // tl.a.bits.user(MatrixKey):= 1.U
    when(subMLU.io.MLU_L2_io.Cacheline_Read_io(i).valid) {
      val (legal, get_bits) = edge.Get(
        fromSource = subMLU.io.MLU_L2_io.Cacheline_Read_io(i).id,
        toAddress = subMLU.io.MLU_L2_io.Cacheline_Read_io(i).addr,
        lgSize = 6.U // 64 bytes = 2^6
      )
      
      tl.a.valid := legal
      tl.a.bits := get_bits
      tl.a.bits.user(MatrixKey):= 1.U// Mark as Matrix request
    }.otherwise {
      tl.a.bits.user(MatrixKey):= 0.U
      tl.a.valid := false.B
    }

    // D channel - Only used for Put responses
    tl.d.ready := true.B

    // We don't use B, C channels for this interface
    tl.b.ready := true.B
    tl.c.valid := false.B
    tl.e.valid := false.B
  }
}

// // AME Core Module
// class AMECore(implicit p: Parameters) extends Module with AMEParameter {
//   val io = IO(new AMEIO)

//   // Instantiate submodules
//   val subMMAU = Module(new MMAU)
//   val subRegFile = Module(new RegFile)
//   val subExpander = Module(new Expander)
//   val subScoreBoard = Module(new ScoreBoard)
//   val subMLU = Module(new MLU)

//   // Debug signal
//   io.sigDone := subExpander.io.sigDone

//   // RegFile connections
//   subRegFile.io := DontCare
//   io.writeAll <> subRegFile.io.writeAll(1)
//   io.readAll <> subRegFile.io.readAll(1)

//   // Expander connections
//   io.Uop_io <> subExpander.io.Uop_io

//   // MMAU and RegFile connections
//   connectPort.toTrReadPort(
//     subRegFile.io.readTr(0),
//     subMMAU.io.Ops_io.ms1,
//     subMMAU.io.actPortReadA,
//     subMMAU.io.addrReadA,
//     subMMAU.io.vecA
//   )

//   connectPort.toTrReadPort(
//     subRegFile.io.readTr(1),
//     subMMAU.io.Ops_io.ms2,
//     subMMAU.io.actPortReadB,
//     subMMAU.io.addrReadB,
//     subMMAU.io.vecB
//   )

//   connectPort.toAccReadPort(
//     subRegFile.io.readAcc(0),
//     subMMAU.io.Ops_io.md,
//     subMMAU.io.actPortReadC,
//     subMMAU.io.addrReadC,
//     subMMAU.io.vecCin
//   )

//   connectPort.toAccWritePort(
//     subRegFile.io.writeAcc(0),
//     subMMAU.io.Ops_io.md,
//     subMMAU.io.actPortWriteC,
//     subMMAU.io.addrWriteC,
//     subMMAU.io.vecCout,
//     subMMAU.io.sigEnWriteC
//   )

//   // MMAU and Expander connections
//   subMMAU.io.FSM_MMAU_io <> subExpander.io.FSM_MMAU_io

//   // RegFile and MLU connections
//   subRegFile.io.writeAll(0) <> subMLU.io.RegFileAllWrite_io

//   // Expander and ScoreBoard connections
//   subExpander.io.ScoreboardVisit_io <> subScoreBoard.io.ScoreboardVisit_io

//   // Expander and MLU connections
//   subMLU.io.FSM_MLU_io <> subExpander.io.FSM_MLU_io
// } 