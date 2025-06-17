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
import AME._
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

// AME IO Bundle
class AMEIO(implicit p: Parameters) extends Bundle {
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
  
  // Get TileLink interfaces for all 8 matrix nodes
  val (tls, edges) = outer.matrix_nodes.map(_.out.head).unzip

  val ame = Module(new AME)
  ame.io.Uop_io <> io.Uop_io
  ame.io.writeAll <> io.writeAll
  ame.io.readAll <> io.readAll
  io.sigDone := ame.io.sigDone

  // M channel handling (for Get responses)
  val mlu_read_io = ame.io.MLU_L2_io.Cacheline_Read_io
  val mlu_readback_io = ame.io.MLU_L2_io.Cacheline_ReadBack_io
  val matrix_data_in = io.matrix_data_in

  for (i <- 0 until 8) {
    matrix_data_in(i).ready := true.B  // AME is receiver in M channel

    when(matrix_data_in(i).valid) {
      mlu_readback_io(i).valid := true.B
      mlu_readback_io(i).data := matrix_data_in(i).bits.data.data
      mlu_readback_io(i).id := matrix_data_in(i).bits.sourceId
    }.otherwise {
      mlu_readback_io(i).valid := false.B
      mlu_readback_io(i).data := 0.U
      mlu_readback_io(i).id := 0.U
    }
  }

  // Connect each MLU Cacheline interface to its corresponding TileLink node
  for (i <- 0 until ame.io.MLU_L2_io.Cacheline_Read_io.length) {
    println(s"Connecting MLU Cacheline interface $i to TileLink node")
    val tl = tls(i)
    val edge = edges(i)

    // A channel - Get request handling
    // tl.a.bits.user(MatrixKey):= 1.U

    mlu_read_io(i).ready := tl.a.ready

    when(mlu_read_io(i).valid) {
      val (legal, get_bits) = edge.Get(
        fromSource = mlu_read_io(i).id,
        toAddress = mlu_read_io(i).addr,
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